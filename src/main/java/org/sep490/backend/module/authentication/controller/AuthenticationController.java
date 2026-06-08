package org.sep490.backend.module.authentication.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.authentication.dto.request.LoginRequest;
import org.sep490.backend.module.authentication.dto.request.RegistrationRequest;
import org.sep490.backend.module.authentication.dto.request.SendOtpRequest;
import org.sep490.backend.module.authentication.dto.request.VerifyOtpRequest;
import org.sep490.backend.module.authentication.dto.response.LoginResponse;
import org.sep490.backend.module.authentication.dto.response.RegistrationResponse;
import org.sep490.backend.module.authentication.service.AuthService;
import org.springframework.http.ResponseEntity;
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
}
