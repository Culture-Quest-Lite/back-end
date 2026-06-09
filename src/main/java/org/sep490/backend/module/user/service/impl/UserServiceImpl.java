package org.sep490.backend.module.user.service.impl;

import lombok.RequiredArgsConstructor;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.sep490.backend.module.authentication.mapper.UserMapper;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.user.dto.request.UpdateProfileRequest;
import org.sep490.backend.module.user.dto.response.FollowUserResponse;
import org.sep490.backend.module.user.dto.response.UserProfileResponse;
import org.sep490.backend.module.user.entity.UserFollow;
import org.sep490.backend.module.user.repository.UserFollowRepository;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserFollowRepository userFollowRepository;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(String keycloakUserId) {
        User user = userRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Tài khoản của bạn chưa được kích hoạt hoặc đã bị khóa");
        }

        return userMapper.toProfileResponse(user);
    }

    @Override
    public UserProfileResponse getProfile(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Tài khoản người dùng này hiện đang bị khóa hoặc chưa được kích hoạt");
        }
        return userMapper.toProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateMyProfile(String keycloakUserId, UpdateProfileRequest request) {
        User user = userRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Tài khoản của bạn không ở trạng thái hoạt động để cập nhật");
        }
        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName().trim());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
        }
        if (request.getAutoPlayAudio() != null) {
            user.setAutoPlayAudio(request.getAutoPlayAudio());
        }

        user = userRepository.save(user);
        return userMapper.toProfileResponse(user);
    }

    @Override
    @Transactional
    public void followUser(String currentKeycloakUserId, Long targetUserId) {
        User follower = userRepository.findByKeycloakUserId(currentKeycloakUserId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));
        if (follower.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Tài khoản của bạn bị khóa hoặc chưa kích hoạt");
        }

        User following = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException("Người dùng cần theo dõi không tồn tại"));

        if (following.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Người dùng này hiện không hoạt động");
        }

        if (follower.getUserId().equals(targetUserId)) {
            throw new BusinessException("Bạn không thể tự theo dõi chính mình");
        }

        UserFollow userFollow = UserFollow.builder()
                .follower(follower)
                .following(following)
                .build();
        userFollowRepository.save(userFollow);
    }

    @Override
    @Transactional
    public void unfollowUser(String currentKeycloakUserId, Long targetUserId) {
        User follower = userRepository.findByKeycloakUserId(currentKeycloakUserId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));
        User following = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException("Người dùng cần bỏ theo dõi không tồn tại"));
        UserFollow userFollow = userFollowRepository.findByFollowerAndFollowing(follower, following)
                .orElseThrow(() -> new BusinessException("Bạn chưa theo dõi người dùng này"));
        userFollowRepository.delete(userFollow);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowUserResponse> getFollowers(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));
        return userFollowRepository.findAllByFollowing(user).stream()
                .map(follow -> {
                    User f = follow.getFollower();
                    return FollowUserResponse.builder()
                            .userId(f.getUserId())
                            .username(f.getUsername())
                            .displayName(f.getDisplayName())
                            .avatarUrl(f.getAvatarUrl())
                            .levelName(f.getLevel() != null ? f.getLevel().getName() : null)
                            .build();
                }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowUserResponse> getFollowings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));
        return userFollowRepository.findAllByFollower(user).stream()
                .map(follow -> {
                    User f = follow.getFollowing();
                    return FollowUserResponse.builder()
                            .userId(f.getUserId())
                            .username(f.getUsername())
                            .displayName(f.getDisplayName())
                            .avatarUrl(f.getAvatarUrl())
                            .levelName(f.getLevel() != null ? f.getLevel().getName() : null)
                            .build();
                }).toList();
    }
}
