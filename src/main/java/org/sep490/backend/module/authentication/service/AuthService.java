package org.sep490.backend.module.authentication.service;

import org.sep490.backend.module.authentication.dto.request.*;
import org.sep490.backend.module.authentication.dto.response.LoginResponse;
import org.sep490.backend.module.authentication.dto.response.RegistrationResponse;

public interface AuthService {
    RegistrationResponse register(RegistrationRequest request);
    void verifyEmailWithOtp(VerifyOtpRequest request);
    void resendOtp(SendOtpRequest request);
    LoginResponse login(LoginRequest request);
    void logout(LogoutRequest request);
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
    void changePassword(String keycloakUserId, ChangePasswordRequest request);
    LoginResponse loginGoogle(String code, String redirectUri);
    LoginResponse loginFacebook(String code, String redirectUri);
}
