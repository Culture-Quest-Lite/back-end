package org.sep490.backend.module.authorization.util;

import org.sep490.backend.module.authorization.service.PermissionCacheService;
import org.sep490.backend.module.user.entity.enumeration.UserRole;

import java.util.HashSet;
import java.util.Set;

public final class PermissionResolver {
    private PermissionResolver() {}

    public static UserRole parseRole(String name) {
        if (name == null) return null;

        try {
            return UserRole.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Set<String> merge(Set<String> rolePermissions,
                                    Set<PermissionCacheService.UserPermissionView> overrides) {
        Set<String> result = new HashSet<>(rolePermissions);
        overrides.stream().filter(PermissionCacheService.UserPermissionView::granted)
                .forEach(o -> result.add(o.code()));
        overrides.stream().filter(o -> !o.granted())
                .forEach(o -> result.remove(o.code()));
        return result;
    }
}
