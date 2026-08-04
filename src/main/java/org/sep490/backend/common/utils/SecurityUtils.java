package org.sep490.backend.common.utils;

import org.sep490.backend.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

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

    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();

            Object claimValue = jwt.getClaim("custom_internal_user_id");

            if (claimValue instanceof Number) {
                return ((Number) claimValue).longValue();
            }
        }

        throw new BusinessException("User chưa đăng nhập hoặc token không hợp lệ");
    }
}
