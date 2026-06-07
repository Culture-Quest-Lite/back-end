package org.sep490.backend.module.authentication.service;

import org.sep490.backend.module.authentication.dto.request.RegistrationRequest;
import org.sep490.backend.module.authentication.dto.response.RegistrationResponse;

public interface UserService {
    RegistrationResponse register(RegistrationRequest request);
}
