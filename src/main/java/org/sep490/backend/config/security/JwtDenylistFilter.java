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

/**
 * Chặn access token đã logout.
 *
 * Keycloak thu hồi được refresh token nhưng KHÔNG thu hồi access token đã cấp —
 * token đó vẫn hợp lệ tới khi hết hạn tự nhiên. Filter này đóng khoảng trống đó.
 *
 * ĐÁNH ĐỔI: thêm một round-trip Redis (~0.2ms) vào MỌI request đã xác thực.
 * Với một instance thì chấp nhận được, và nó vá một lỗ hổng thật.
 *
 * FAIL-OPEN CÓ CHỦ Ý: Redis chết thì cho request đi qua, không chặn toàn bộ người dùng.
 * Nhất quán với nguyên tắc "Redis chết → app vẫn chạy". Rủi ro là trong lúc Redis chết,
 * token đã logout tạm thời dùng lại được — chấp nhận được so với việc sập toàn hệ thống.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtDenylistFilter extends OncePerRequestFilter {

    private final AuthTokenService authTokenService;

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
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            "{\"status\":401,\"errorCode\":\"TOKEN_REVOKED\","
                                    + "\"message\":\"Phiên đăng nhập đã kết thúc. Vui lòng đăng nhập lại\"}");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
