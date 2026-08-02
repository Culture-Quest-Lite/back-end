package org.sep490.backend.module.user.service;

import org.sep490.backend.common.filter.dto.BaseFilterRequest;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.user.dto.filter.LeaderboardFilterRequest;
import org.sep490.backend.module.user.dto.request.UpdateProfileRequest;
import org.sep490.backend.module.user.dto.response.FollowStatusResponse;
import org.sep490.backend.module.user.dto.response.FollowUserResponse;
import org.sep490.backend.module.user.dto.response.LeaderboardEntryResponse;
import org.sep490.backend.module.user.dto.response.MyLeaderboardRankResponse;
import org.sep490.backend.module.user.dto.response.UserProfileResponse;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

public interface UserService {
    UserProfileResponse getMyProfile(String keycloakUserId);
    UserProfileResponse getProfile(Long id);
    UserProfileResponse updateMyProfile(String keycloakUserId, UpdateProfileRequest request);
    FollowStatusResponse followUser(String currentKeycloakUserId, Long targetUserId);
    FollowStatusResponse unfollowUser(String currentKeycloakUserId, Long targetUserId);
    List<FollowUserResponse> getFollowers(Long userId);
    List<FollowUserResponse> getFollowings(Long userId);
    Page<UserProfileResponse> getAllUsersWithFilter(BaseFilterRequest filterRequest);
    Page<LeaderboardEntryResponse> getXpLeaderboard(LeaderboardFilterRequest filter);
    MyLeaderboardRankResponse getMyXpRank();
    void lockUser(Long id);
    void unlockUser(Long id);
    void updateUserRole(Long userId, UserRole role);
    User getCurrentUser();
    User getUserById(Long id);
    List<User> getUsersByIds(List<Long> ids);
    List<FollowUserResponse> getFriends();
}
