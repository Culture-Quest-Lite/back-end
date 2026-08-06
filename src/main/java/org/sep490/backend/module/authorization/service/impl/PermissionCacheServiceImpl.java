package org.sep490.backend.module.authorization.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.config.redis.CacheNames;
import org.sep490.backend.module.authorization.repository.RolePermissionRepository;
import org.sep490.backend.module.authorization.repository.UserPermissionRepository;
import org.sep490.backend.module.authorization.service.PermissionCacheService;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionCacheServiceImpl implements PermissionCacheService {

    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionRepository userPermissionRepository;

    @Override
    @Cacheable(value = CacheNames.ROLE_PERMISSIONS, key = "#role.name()")
    @Transactional(readOnly = true)
    public Set<String> getByRole(UserRole role) {
        return new HashSet<>(rolePermissionRepository.findPermissionCodesByRole(role));
    }

    @Override
    @Cacheable(value = CacheNames.USER_PERMISSION_OVERRIDES, key = "#userId")
    @Transactional(readOnly = true)
    public Set<UserPermissionView> getUserOverrides(Long userId) {
        return userPermissionRepository.findActiveByUserId(userId).stream()
                .map(up -> new UserPermissionView(up.getPermission().getCode(), up.isGranted()))
                .collect(Collectors.toSet());
    }

    @Override
    @CacheEvict(value = CacheNames.ROLE_PERMISSIONS, allEntries = true)
    public void evictAllRoles() {
        log.info("Đã xóa cache quyền theo vai trò, có hiệu lực từ request kế tiếp");
    }

    @Override
    @CacheEvict(value = CacheNames.USER_PERMISSION_OVERRIDES, key = "#userId")
    public void evictUser(Long userId) {
        log.info("Đã xóa cache quyền riêng của user {}", userId);
    }
}
