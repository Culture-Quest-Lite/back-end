package org.sep490.backend.module.authentication.service;

import org.sep490.backend.module.authentication.dto.request.RegistrationRequest;
import org.sep490.backend.module.authentication.dto.request.SendOtpRequest;
import org.sep490.backend.module.authentication.dto.request.VerifyOtpRequest;
import org.sep490.backend.module.authentication.dto.response.RegistrationResponse;

public interface AuthService {
    RegistrationResponse register(RegistrationRequest request);
    void verifyEmailWithOtp(VerifyOtpRequest request);
    void resendOtp(SendOtpRequest request);
}
