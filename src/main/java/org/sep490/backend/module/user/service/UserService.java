package org.sep490.backend.module.user.service;

import org.sep490.backend.module.user.dto.request.UpdateProfileRequest;
import org.sep490.backend.module.user.dto.response.UserProfileResponse;

public interface UserService {
    UserProfileResponse getMyProfile(String keycloakUserId);
    UserProfileResponse getProfile(Long id);
    UserProfileResponse updateMyProfile(String keycloakUserId, UpdateProfileRequest request);
}
