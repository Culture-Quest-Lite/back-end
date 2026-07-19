package org.sep490.backend.module.authentication.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.authentication.dto.request.*;
import org.sep490.backend.module.authentication.dto.response.LoginResponse;
import org.sep490.backend.module.authentication.dto.response.MobileLoginResponse;
import org.sep490.backend.module.authentication.service.AuthService;
import org.sep490.backend.module.user.dto.response.UserProfileResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Authentication controller hỗ trợ cả Web và Mobile client.
 *
 * Web client: gửi header X-Client-Type: web (hoặc không gửi)
 * → Refresh token được lưu trong HttpOnly Cookie
 * → Response body KHÔNG chứa refresh token
 *
 * Mobile client: gửi header X-Client-Type: mobile
 * → Refresh token trả về trong response body
 * → Client tự lưu vào Android Keystore / iOS Keychain
 * → /refresh-token và /logout nhận token qua request body
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {

        private static final String CLIENT_TYPE_HEADER = "X-Client-Type";
        private static final String MOBILE = "mobile";

        private final AuthService authService;

        @PostMapping("/register")
        public ResponseEntity<UserProfileResponse> register(@Valid @RequestBody RegistrationRequest request) {
                UserProfileResponse response = authService.register(request);
                return ResponseEntity.ok(response);
        }

        @PostMapping("/verify-otp")
        public ResponseEntity<Map<String, String>> verifyOtp(
                        @Valid @RequestBody VerifyOtpRequest request) {
                authService.verifyEmailWithOtp(request);
                Map<String, String> response = new HashMap<>();
                response.put("message", "Xác thực email thành công! Tài khoản của bạn đã được kích hoạt");
                return ResponseEntity.ok(response);
        }

        @PostMapping("/resend-otp")
        public ResponseEntity<Map<String, String>> resendOtp(
                        @Valid @RequestBody SendOtpRequest request) {
                authService.resendOtp(request);
                Map<String, String> response = new HashMap<>();
                response.put("message", "Mã OTP đã được gửi lại thành công");
                return ResponseEntity.ok(response);
        }

        @PostMapping("/login")
        public ResponseEntity<?> login(
                        @Valid @RequestBody LoginRequest request,
                        @RequestHeader(value = CLIENT_TYPE_HEADER, defaultValue = "web") String clientType) {
                LoginResponse loginResponse = authService.login(request, clientType);
                return buildTokenResponse(loginResponse, clientType);
        }

        @PostMapping("/login-by-google")
        public ResponseEntity<?> loginGoogle(
                        @Valid @RequestBody SocialLoginRequest request,
                        @RequestHeader(value = CLIENT_TYPE_HEADER, defaultValue = "web") String clientType) {
                LoginResponse loginResponse = authService.loginGoogle(request.getCode(), request.getRedirectUri(),
                                clientType);
                return buildTokenResponse(loginResponse, clientType);
        }

        @PostMapping("/login-by-facebook")
        public ResponseEntity<?> loginFacebook(
                        @Valid @RequestBody SocialLoginRequest request,
                        @RequestHeader(value = CLIENT_TYPE_HEADER, defaultValue = "web") String clientType) {
                LoginResponse loginResponse = authService.loginFacebook(request.getCode(), request.getRedirectUri());
                return buildTokenResponse(loginResponse, clientType);
        }

        @PostMapping("/refresh-token")
        public ResponseEntity<?> refreshToken(
                        @CookieValue(name = "refresh_token", required = false) String cookieRefreshToken,
                        @RequestBody(required = false) LogoutRequest body,
                        @RequestHeader(value = CLIENT_TYPE_HEADER, defaultValue = "web") String clientType) {
                String token = isMobile(clientType)
                                ? (body != null ? body.getRefreshToken() : null)
                                : cookieRefreshToken;

                LoginResponse loginResponse = authService.refreshToken(token);
                return buildTokenResponse(loginResponse, clientType);
        }

        @PostMapping("/logout")
        public ResponseEntity<Map<String, String>> logout(
                        @CookieValue(name = "refresh_token", required = false) String cookieRefreshToken,
                        @RequestBody(required = false) LogoutRequest body,
                        @RequestHeader(value = CLIENT_TYPE_HEADER, defaultValue = "web") String clientType) {
                String token = isMobile(clientType)
                                ? (body != null ? body.getRefreshToken() : null)
                                : cookieRefreshToken;

                if (token != null && !token.isEmpty()) {
                        authService.logout(token);
                }

                if (isMobile(clientType)) {
                        return ResponseEntity.ok(Map.of("message", "Đăng xuất thành công"));
                }

                ResponseCookie deleteCookie = ResponseCookie.from("refresh_token", "")
                                .httpOnly(true)
                                .secure(true)
                                .path("/api/auth")
                                .maxAge(0)
                                .sameSite("Lax")
                                .build();
                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                                .body(Map.of("message", "Đăng xuất thành công"));
        }

        @PostMapping("/forgot-password")
        public ResponseEntity<String> forgotPassword(
                        @Valid @RequestBody ForgotPasswordRequest request) {
                authService.forgotPassword(request);
                return ResponseEntity.ok("Link đặt lại mật khẩu đã được gửi tới email của bạn");
        }

        @PostMapping("/reset-password")
        public ResponseEntity<String> resetPassword(
                        @Valid @RequestBody ResetPasswordRequest request) {
                authService.resetPassword(request);
                return ResponseEntity.ok("Đặt lại mật khẩu thành công");
        }

        @PostMapping("/change-password")
        public ResponseEntity<Map<String, String>> changePassword(
                        @AuthenticationPrincipal Jwt jwt,
                        @Valid @RequestBody ChangePasswordRequest request) {
                String keycloakUserId = jwt.getSubject();
                authService.changePassword(keycloakUserId, request);
                return ResponseEntity.ok(Map.of("message", "Thay đổi mật khẩu thành công"));
        }

        private boolean isMobile(String clientType) {
                return MOBILE.equalsIgnoreCase(clientType);
        }

        private ResponseEntity<?> buildTokenResponse(LoginResponse loginResponse, String clientType) {
                if (isMobile(clientType)) {
                        MobileLoginResponse mobileResponse = MobileLoginResponse.builder()
                                        .accessToken(loginResponse.getAccessToken())
                                        .tokenType(loginResponse.getTokenType())
                                        .expiresIn(loginResponse.getExpiresIn())
                                        .refreshToken(loginResponse.getRefreshToken())
                                        .refreshExpiresIn(loginResponse.getRefreshExpiresIn())
                                        .build();
                        return ResponseEntity.ok(mobileResponse);
                }

                ResponseCookie cookie = buildRefreshCookie(loginResponse);
                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                                .body(loginResponse);
        }

        private ResponseCookie buildRefreshCookie(LoginResponse loginResponse) {
                String token = loginResponse.getRefreshToken();
                Long maxAge = loginResponse.getRefreshExpiresIn();
                if (token == null || token.isBlank()) {
                        throw new BusinessException("Không nhận được refresh token từ server xác thực");
                }
                return ResponseCookie.from("refresh_token", token)
                                .httpOnly(true)
                                .secure(true)
                                .path("/api/auth")
                                .maxAge(maxAge != null ? maxAge : 86400L)
                                .sameSite("Lax")
                                .build();
        }
}
