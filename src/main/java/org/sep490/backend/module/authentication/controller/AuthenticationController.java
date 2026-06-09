package org.sep490.backend.module.authentication.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.authentication.dto.request.*;
import org.sep490.backend.module.authentication.dto.response.LoginResponse;
import org.sep490.backend.module.authentication.dto.response.RegistrationResponse;
import org.sep490.backend.module.authentication.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
        RegistrationResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, String>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request
    ) {
        authService.verifyEmailWithOtp(request);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Xác thực email thành công! Tài khoản của bạn đã được kích hoạt");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<Map<String, String>> resendOtp(
            @Valid @RequestBody SendOtpRequest request
    ) {
        authService.resendOtp(request);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Mã OTP đã được gửi lại thành công");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
            ) {
    LoginResponse response = authService.login(request);
    return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @Valid @RequestBody LogoutRequest request
    ) {
        authService.logout(request);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Đăng xuất thành công");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        authService.forgotPassword(request);
        return ResponseEntity.ok("Link đặt lại mật khẩu đã được gửi tới email của bạn");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        authService.resetPassword(request);
        return ResponseEntity.ok("Đặt lại mật khẩu thành công");
    }

    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        String keycloakUserId = jwt.getSubject();
        authService.changePassword(keycloakUserId, request);
        return ResponseEntity.ok("Thay đổi mật khẩu thành công");
    }

    @PostMapping("/login-by-google")
    public ResponseEntity<LoginResponse> loginGoogle(@Valid @RequestBody SocialLoginRequest request) {
        LoginResponse response = authService.loginGoogle(request.getCode(), request.getRedirectUri());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login-by-facebook")
    public ResponseEntity<LoginResponse> loginFacebook(
            @Valid @RequestBody SocialLoginRequest request
    ) {
        LoginResponse response = authService.loginFacebook(request.getCode(), request.getRedirectUri());
        return ResponseEntity.ok(response);
    }
}
