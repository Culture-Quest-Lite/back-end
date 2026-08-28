package org.sep490.backend.config.security;

import org.sep490.backend.module.authentication.service.AuthTokenService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtDenylistFilter - chặn token đã logout")
class JwtDenylistFilterTest {

    @Mock
    private AuthTokenService authTokenService;

    @Mock
    private FilterChain filterChain;

    /**
     * @Spy chứ không @Mock: test dưới khẳng định body chứa "TOKEN_REVOKED",
     * mà mock rỗng thì không ghi gì ra response.
     */
    @Spy
    private SecurityResponseWriter responseWriter =
            new SecurityResponseWriter(new ObjectMapper().registerModule(new JavaTimeModule()));

    @InjectMocks
    private JwtDenylistFilter filter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWithJti(String jti) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .jti(jti)
                .subject("kc-001")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of()));
    }

    @Test
    @DisplayName("Token trong denylist bị chặn với 401")
    void tokenBiChan() throws Exception {
        authenticateWithJti("jti-revoked");
        when(authTokenService.isTokenDenied("jti-revoked")).thenReturn(true);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("TOKEN_REVOKED");
        verify(filterChain, never()).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Token bình thường đi qua")
    void tokenHopLeDiQua() throws Exception {
        authenticateWithJti("jti-ok");
        when(authTokenService.isTokenDenied("jti-ok")).thenReturn(false);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("FAIL-OPEN: Redis lỗi thì vẫn cho request đi qua")
    void redisLoiThiFailOpen() throws Exception {
        authenticateWithJti("jti-any");
        when(authTokenService.isTokenDenied(anyString()))
                .thenThrow(new RuntimeException("Redis command timed out"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, filterChain);

        // Redis chết KHÔNG được làm sập toàn bộ hệ thống
        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Request chưa đăng nhập đi qua, không gọi Redis")
    void chuaDangNhapKhongGoiRedis() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, filterChain);

        verify(authTokenService, never()).isTokenDenied(anyString());
        verify(filterChain).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Authentication không phải JWT thì bỏ qua")
    void authKhongPhaiJwt() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "pass", List.of()));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, filterChain);

        verify(authTokenService, never()).isTokenDenied(anyString());
        verify(filterChain).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
