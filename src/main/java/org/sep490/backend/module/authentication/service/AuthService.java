package org.sep490.backend.module.authentication.service;

import org.sep490.backend.module.authentication.dto.request.*;
import org.sep490.backend.module.authentication.dto.response.LoginResponse;
import org.sep490.backend.module.user.dto.response.UserProfileResponse;

public interface AuthService {
    UserProfileResponse register(RegistrationRequest request);
    void verifyEmailWithOtp(VerifyOtpRequest request);
    void resendOtp(SendOtpRequest request);
    LoginResponse login(LoginRequest request, String clientType);
    LoginResponse refreshToken(String refreshToken);
    void logout(String refreshToken);
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
    void changePassword(String keycloakUserId, ChangePasswordRequest request);
    LoginResponse loginGoogle(String code, String redirectUri, String clientType);
    LoginResponse loginFacebook(String code, String redirectUri);
}
