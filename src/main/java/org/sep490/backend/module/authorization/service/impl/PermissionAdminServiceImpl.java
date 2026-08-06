package org.sep490.backend.module.authorization.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.authorization.constant.PermissionCode;
import org.sep490.backend.module.authorization.dto.request.GrantUserPermissionRequest;
import org.sep490.backend.module.authorization.dto.response.PermissionGroupResponse;
import org.sep490.backend.module.authorization.dto.response.PermissionResponse;
import org.sep490.backend.module.authorization.dto.response.RolePermissionMatrixResponse;
import org.sep490.backend.module.authorization.dto.response.UserPermissionResponse;
import org.sep490.backend.module.authorization.entity.Permission;
import org.sep490.backend.module.authorization.entity.RolePermission;
import org.sep490.backend.module.authorization.entity.UserPermission;
import org.sep490.backend.module.authorization.repository.PermissionRepository;
import org.sep490.backend.module.authorization.repository.RolePermissionRepository;
import org.sep490.backend.module.authorization.repository.UserPermissionRepository;
import org.sep490.backend.module.authorization.service.PermissionAdminService;
import org.sep490.backend.module.authorization.service.PermissionCacheService;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionAdminServiceImpl implements PermissionAdminService {

    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final UserRepository userRepository;
    private final PermissionCacheService permissionCacheService;


    @Override
    @Transactional(readOnly = true)
    public List<PermissionGroupResponse> listGrouped() {
        return permissionRepository.findAllByActiveTrueOrderByGroupNameAscCodeAsc()
                .stream()
                .collect(Collectors.groupingBy(
                        p -> p.getGroupName() == null ? "KHÁC" : p.getGroupName(),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(e -> PermissionGroupResponse.builder()
                        .groupName(e.getKey())
                        .permissions(e.getValue().stream().map(this::toResponse).toList())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RolePermissionMatrixResponse getMatrix() {
        Map<UserRole, Set<String>> matrix = new EnumMap<>(UserRole.class);
        for (UserRole role : UserRole.values()) {
            matrix.put(role, new HashSet<>(rolePermissionRepository.findPermissionCodesByRole(role)));
        }
        return RolePermissionMatrixResponse.builder()
                .allPermissions(listGrouped())
                .matrix(matrix)
                .build();
    }

    @Override
    @Transactional
    public void replacePermissions(UserRole role, List<String> codes) {
        List<String> distinct = codes.stream().distinct().toList();
        validateCodesExist(distinct);
        guardAdminLockout(role, distinct);

        rolePermissionRepository.deleteByRole(role);
        rolePermissionRepository.flush();

        for (String code : distinct) {
            Permission permission = permissionRepository.findByCode(code)
                    .orElseThrow(() -> new BusinessException("Mã quyền không tồn tại: {}", code));
            rolePermissionRepository.save(
                    RolePermission.builder()
                            .role(role)
                            .permission(permission)
                            .build());
        }

        permissionCacheService.evictAllRoles();
        log.info("Cập nhật {} quyền cho vai trò {}", distinct.size(), role);
    }

    @Override
    @Transactional
    public void grant(UserRole role, String code) {
        if (rolePermissionRepository.existsByRoleAndPermission_Code(role, code)) {
            return;   // idempotent
        }
        Permission permission = permissionRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException("Mã quyền không tồn tại: {}", code));
        rolePermissionRepository.save(
                RolePermission.builder().role(role).permission(permission).build());
        permissionCacheService.evictAllRoles();

    }

    @Override
    @Transactional
    public void revoke(UserRole role, String code) {
        if (role == UserRole.ADMIN && PermissionCode.PERMISSION_MANAGE.equals(code)) {
            throw new BusinessException("Không thể gỡ quyền cấu hình phân quyền khỏi vai trò ADMIN");
        }
        rolePermissionRepository.deleteByRoleAndPermission_Code(role, code);
        permissionCacheService.evictAllRoles();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserPermissionResponse> getUserPermissions(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return userPermissionRepository.findAllByUserIdWithPermission(userId).stream()
                .map(up -> UserPermissionResponse.builder()
                        .id(up.getId())
                        .code(up.getPermission().getCode())
                        .description(up.getPermission().getDescription())
                        .granted(up.isGranted())
                        .expiresAt(up.getExpiresAt())
                        .reason(up.getReason())
                        .expired(up.getExpiresAt() != null && up.getExpiresAt().isBefore(now))
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public void upsertUserPermission(Long userId, GrantUserPermissionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy người dùng"));
        Permission permission = permissionRepository.findByCode(request.getCode())
                .orElseThrow(() -> new BusinessException("Mã quyền không tồn tại: {}", request.getCode()));

        guardSelfLockout(user, request);

        UserPermission entity = userPermissionRepository
                .findByUser_UserIdAndPermission_Code(userId, request.getCode())
                .orElseGet(() -> UserPermission.builder()
                        .user(user)
                        .permission(permission)
                        .build());

        entity.setGranted(request.getGranted());
        entity.setExpiresAt(request.getExpiresAt());
        entity.setReason(request.getReason());
        userPermissionRepository.save(entity);

        permissionCacheService.evictUser(userId);
        log.info("{} quyền {} cho user {} — lý do: {}",
                request.getGranted() ? "Cấp" : "Thu hồi",
                request.getCode(), userId, request.getReason());
    }

    @Override
    @Transactional
    public void deleteUserPermission(Long userId, String code) {
        userPermissionRepository.deleteByUser_UserIdAndPermission_Code(userId, code);
        permissionCacheService.evictUser(userId);
    }

    private void validateCodesExist(List<String> codes) {
        List<String> invalid = codes.stream()
                .filter(c -> !permissionRepository.existsByCode(c))
                .toList();
        if (!invalid.isEmpty()) {
            throw new BusinessException("Mã quyền không tồn tại: {}", String.join(", ", invalid));
        }
    }

    private void guardAdminLockout(UserRole role, List<String> codes) {
        if (role == UserRole.ADMIN && !codes.contains(PermissionCode.PERMISSION_MANAGE)) {
            throw new BusinessException("Không thể gỡ quyền cấu hình phân quyền khỏi vai trò ADMIN");
        }
    }

    private void guardSelfLockout(User user, GrantUserPermissionRequest request) {
        boolean revokingPermissionManage =
                !request.getGranted() && PermissionCode.PERMISSION_MANAGE.equals(request.getCode());
        if (revokingPermissionManage && user.getRole() == UserRole.ADMIN) {
            throw new BusinessException(
                    "Không thể thu hồi quyền cấu hình phân quyền của một tài khoản ADMIN");
        }
    }

    private PermissionResponse toResponse(Permission p) {
        return PermissionResponse.builder()
                .permissionId(p.getPermissionId())
                .code(p.getCode())
                .groupName(p.getGroupName())
                .description(p.getDescription())
                .active(p.isActive())
                .build();
    }
}
