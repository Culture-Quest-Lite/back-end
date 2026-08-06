package org.sep490.backend.common.utils;

import org.sep490.backend.module.authorization.constant.PermissionCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class SecurityUtils {

    public static Optional<Jwt> getCurrentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuthToken) {
            return Optional.of(jwtAuthToken.getToken());
        }

        return Optional.empty();
    }

    public static Optional<String> getCurrentUserKeyCloakId() {
        return getCurrentJwt().map(Jwt::getSubject);
    }

    public static Set<String> getCurrentAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Set.of();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    public static boolean hasPermission(String permissionCode) {
        return getCurrentAuthorities().contains(PermissionCode.PREFIX + permissionCode);
    }

    public static boolean hasRole(String role) {
        return getCurrentAuthorities().contains("ROLE_" + role);
    }
}
