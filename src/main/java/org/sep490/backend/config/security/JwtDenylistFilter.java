package org.sep490.backend.config.security;

import org.sep490.backend.module.authentication.service.AuthTokenService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtDenylistFilter extends OncePerRequestFilter {

    private final AuthTokenService authTokenService;
    private final SecurityResponseWriter responseWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String jti = jwt.getId();
            if (jti != null) {
                boolean denied;
                try {
                    denied = authTokenService.isTokenDenied(jti);
                } catch (Exception e) {
                    log.warn("Không kiểm tra được denylist, cho request đi qua: {}", e.getMessage());
                    denied = false;
                }

                if (denied) {
                    SecurityContextHolder.clearContext();
                    responseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                            "TOKEN_REVOKED", "Phiên đăng nhập đã kết thúc. Vui lòng đăng nhập lại");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
