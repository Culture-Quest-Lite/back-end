package org.sep490.backend.module.user.service;

import org.sep490.backend.module.user.dto.request.UpdateProfileRequest;
import org.sep490.backend.module.user.dto.response.FollowUserResponse;
import org.sep490.backend.module.user.dto.response.UserProfileResponse;

import java.util.List;

public interface UserService {
    UserProfileResponse getMyProfile(String keycloakUserId);
    UserProfileResponse getProfile(Long id);
    UserProfileResponse updateMyProfile(String keycloakUserId, UpdateProfileRequest request);
    void followUser(String currentKeycloakUserId, Long targetUserId);
    void unfollowUser(String currentKeycloakUserId, Long targetUserId);
    List<FollowUserResponse> getFollowers(Long userId);
    List<FollowUserResponse> getFollowings(Long userId);
}
