package org.sep490.backend.module.authorization.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.module.authorization.entity.Permission;
import org.sep490.backend.module.authorization.entity.RolePermission;
import org.sep490.backend.module.authorization.repository.PermissionRepository;
import org.sep490.backend.module.authorization.repository.RolePermissionRepository;
import org.sep490.backend.module.authorization.service.PermissionCacheService;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.sep490.backend.module.authorization.constant.PermissionCode.*;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.permission.seed-enabled", matchIfMissing = true)
public class PermissionSeeder implements ApplicationRunner {
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionCacheService permissionCacheService;

    private static final Map<String, String[]> CATALOG = new LinkedHashMap<>();

    static {
        CATALOG.put(USER_VIEW_ALL, new String[]{"USER", "Xem danh sách toàn bộ người dùng"});
        CATALOG.put(USER_LOCK, new String[]{"USER", "Khoá / mở khoá tài khoản"});
        CATALOG.put(USER_UPDATE_ROLE, new String[]{"USER", "Thay đổi vai trò người dùng"});

        CATALOG.put(POST_CREATE,     new String[]{"POST", "Đăng bài viết"});
        CATALOG.put(POST_UPDATE_ANY, new String[]{"POST", "Sửa bài viết của người khác"});
        CATALOG.put(POST_DELETE_ANY, new String[]{"POST", "Xoá bài viết của người khác"});
        CATALOG.put(POST_MODERATE,   new String[]{"POST", "Duyệt / từ chối / ban bài viết"});

        CATALOG.put(HOTSPOT_MANAGE, new String[]{"CONTENT", "Quản lý địa điểm"});
        CATALOG.put(ROUTE_MANAGE,   new String[]{"CONTENT", "Quản lý tuyến đường"});
        CATALOG.put(STORY_MANAGE,   new String[]{"CONTENT", "Quản lý câu chuyện"});
        CATALOG.put(TAG_MANAGE,     new String[]{"CONTENT", "Quản lý thẻ"});

        CATALOG.put(REVIEW_MODERATE,   new String[]{"REVIEW", "Kiểm duyệt đánh giá"});
        CATALOG.put(REVIEW_DELETE_ANY, new String[]{"REVIEW", "Xoá đánh giá của người khác"});

        CATALOG.put(VOUCHER_MANAGE,         new String[]{"VOUCHER", "Quản lý toàn bộ voucher"});
        CATALOG.put(VOUCHER_PARTNER_MANAGE, new String[]{"VOUCHER", "Đối tác quản lý voucher của mình"});

        CATALOG.put(SUBSCRIPTION_PLAN_MANAGE, new String[]{"SUBSCRIPTION", "Quản lý gói dịch vụ"});
        CATALOG.put(SUBSCRIPTION_VERIFY,      new String[]{"SUBSCRIPTION", "Xác nhận thanh toán gói"});

        CATALOG.put(LEVEL_MANAGE, new String[]{"GAMIFICATION", "Quản lý cấp độ"});

        CATALOG.put(DASHBOARD_ADMIN_VIEW,   new String[]{"SYSTEM", "Xem dashboard quản trị"});
        CATALOG.put(DASHBOARD_CURATOR_VIEW, new String[]{"SYSTEM", "Xem dashboard kiểm duyệt"});
        CATALOG.put(DASHBOARD_PARTNER_VIEW, new String[]{"SYSTEM", "Xem dashboard đối tác"});
        CATALOG.put(AUDIT_LOG_VIEW,         new String[]{"SYSTEM", "Xem nhật ký hệ thống"});
        CATALOG.put(PERMISSION_MANAGE,      new String[]{"SYSTEM", "Cấu hình ma trận phân quyền"});
    }

    private static final Map<UserRole, List<String>> DEFAULT_MATRIX = Map.of(
            UserRole.ADMIN, new ArrayList<>(CATALOG.keySet()),
            UserRole.CURATOR, List.of(
                    HOTSPOT_MANAGE, ROUTE_MANAGE, STORY_MANAGE, TAG_MANAGE,
                    POST_MODERATE, REVIEW_MODERATE, DASHBOARD_CURATOR_VIEW),
            UserRole.PARTNER, List.of(VOUCHER_PARTNER_MANAGE, DASHBOARD_PARTNER_VIEW),
            UserRole.EXPLORER, List.of(POST_CREATE)
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedPermissions();
        seedMatrixEmpty();
    }

    private void seedPermissions() {
        int created = 0;
        for (Map.Entry<String, String[]> e : CATALOG.entrySet()) {
            if (!permissionRepository.existsByCode(e.getKey())) {
                permissionRepository.save(Permission.builder()
                                .code(e.getKey())
                                .groupName(e.getValue()[0])
                                .description(e.getValue()[1])
                                .active(true)
                                .build());
                created++;
            }
        }
        if (created > 0) {
            log.info("Seed {} quyền mới", created);
        }
    }

    private void seedMatrixEmpty() {
        boolean firstRun = rolePermissionRepository.count() == 0;

        // Sau lần seed đầu chỉ bù quyền chưa từng gán cho bất kỳ vai trò nào,
        // để không hồi sinh mapping mà admin đã chủ động gỡ.
        Set<String> alreadyMapped = firstRun
                ? Set.of()
                : new HashSet<>(rolePermissionRepository.findMappedPermissionCodes());

        int created = 0;
        for (Map.Entry<UserRole, List<String>> entry : DEFAULT_MATRIX.entrySet()) {
            for (String code : entry.getValue()) {
                if (alreadyMapped.contains(code)) {
                    continue;
                }

                Optional<Permission> permission = permissionRepository.findByCode(code);
                if (permission.isEmpty()) {
                    continue;
                }

                rolePermissionRepository.save(RolePermission.builder()
                        .role(entry.getKey())
                        .permission(permission.get())
                        .build());
                created++;
            }
        }

        if (created == 0) {
            log.debug("Ma trận phân quyền đã đầy đủ, không cần seed");
            return;
        }

        log.info("Đã seed {} mapping vai trò-quyền mặc định", created);
        try {
            permissionCacheService.evictAllRoles();
        } catch (Exception e) {
            log.warn("Không xoá được cache quyền theo vai trò, sẽ tự hết hạn sau TTL: {}", e.getMessage());
        }
    }
}
