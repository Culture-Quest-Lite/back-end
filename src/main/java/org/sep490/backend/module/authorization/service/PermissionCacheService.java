package org.sep490.backend.module.authorization.service;

import org.sep490.backend.module.user.entity.enumeration.UserRole;

import java.util.Set;

public interface PermissionCacheService {

    Set<String> getByRole(UserRole role);

    Set<UserPermissionView> getUserOverrides(Long userId);

    void evictAllRoles();

    void evictUser(Long userId);

    record UserPermissionView(String code, boolean granted) {}
}
