package org.sep490.backend.module.user.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.common.filter.dto.BaseFilterRequest;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.config.keycloak.KeyCloakAuthClient;
import org.sep490.backend.common.utils.SecurityUtils;
import org.sep490.backend.module.admin.entity.enumeration.AuditAction;
import org.sep490.backend.module.admin.service.AuditLogService;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.sep490.backend.module.authentication.mapper.UserMapper;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.social.repository.PostRepository;
import org.sep490.backend.module.user.dto.request.UpdateProfileRequest;
import org.sep490.backend.module.user.dto.response.FollowStatusResponse;
import org.sep490.backend.module.user.dto.response.FollowUserResponse;
import org.sep490.backend.module.user.dto.response.UserProfileResponse;
import org.sep490.backend.module.user.entity.UserFollow;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.sep490.backend.module.user.repository.UserFollowRepository;
import org.sep490.backend.module.user.service.UserService;
import org.sep490.backend.module.user.specification.UserSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserFollowRepository userFollowRepository;
    private final KeyCloakAuthClient keyCloakAuthClient;
    private final PostRepository postRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(String keycloakUserId) {
        User user = userRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Tài khoản của bạn chưa được kích hoạt hoặc đã bị khóa");
        }

        return enrichProfileResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Tài khoản người dùng này hiện đang bị khóa hoặc chưa được kích hoạt");
        }
        return enrichProfileResponse(user, findCurrentUserOrNull());
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
        if (request.getBackgroundUrl() != null) {
            user.setBackgroundUrl(request.getBackgroundUrl().trim());
        }
        if (request.getAutoPlayAudio() != null) {
            user.setAutoPlayAudio(request.getAutoPlayAudio());
        }

        user = userRepository.save(user);
        return enrichProfileResponse(user);
    }

    @Override
    @Transactional
    public FollowStatusResponse followUser(String currentKeycloakUserId, Long targetUserId) {
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

        //Theo dõi lại người đã theo dõi không phải là lỗi: client chỉ bị lệch state,
        //trả về trạng thái thật để client đồng bộ lại thay vì ném 400.
        if (!userFollowRepository.existsByFollowerAndFollowing(follower, following)) {
            UserFollow userFollow = UserFollow.builder()
                    .follower(follower)
                    .following(following)
                    .build();
            userFollowRepository.save(userFollow);
        }

        return buildFollowStatus(following, true, "Theo dõi người dùng thành công");
    }

    @Override
    @Transactional
    public FollowStatusResponse unfollowUser(String currentKeycloakUserId, Long targetUserId) {
        User follower = userRepository.findByKeycloakUserId(currentKeycloakUserId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));
        if (follower.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Tài khoản của bạn bị khóa hoặc chưa kích hoạt");
        }

        User following = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException("Người dùng cần bỏ theo dõi không tồn tại"));

        userFollowRepository.findByFollowerAndFollowing(follower, following)
                .ifPresent(userFollowRepository::delete);

        return buildFollowStatus(following, false, "Đã hủy theo dõi người dùng");
    }

    private FollowStatusResponse buildFollowStatus(User target, boolean isFollowing, String message) {
        return FollowStatusResponse.builder()
                .userId(target.getUserId())
                .isFollowing(isFollowing)
                .totalFollowers(userFollowRepository.countByFollowing(target))
                .message(message)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowUserResponse> getFollowers(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));
        List<User> followers = userFollowRepository.findAllByFollowing(user).stream()
                .map(UserFollow::getFollower)
                .toList();

        User viewer = findCurrentUserOrNull();
        Set<Long> followedIds = findFollowedIds(viewer, followers.stream().map(User::getUserId).toList());

        return followers.stream()
                .map(f -> toFollowUserResponse(f, viewer, followedIds))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowUserResponse> getFollowings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));
        List<User> followings = userFollowRepository.findAllByFollower(user).stream()
                .map(UserFollow::getFollowing)
                .toList();

        User viewer = findCurrentUserOrNull();
        Set<Long> followedIds = findFollowedIds(viewer, followings.stream().map(User::getUserId).toList());

        return followings.stream()
                .map(f -> toFollowUserResponse(f, viewer, followedIds))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserProfileResponse> getAllUsersWithFilter(BaseFilterRequest filterRequest) {
        Sort sort = filterRequest.getSortDir().equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(filterRequest.getSortBy()).ascending()
                : Sort.by(filterRequest.getSortBy()).descending();
        Pageable pageable = PageRequest.of(filterRequest.getPage(), filterRequest.getSize(), sort);
        Specification<User> spec = UserSpecification.filterUsers(filterRequest.getSearch(), filterRequest.getStatus());
        Page<User> userPage = userRepository.findAll(spec, pageable);
        return userPage.map(this::enrichProfileResponse);
    }

    @Override
    @Transactional
    public void lockUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new BusinessException("Tài khoản này đã bị khóa từ trước");
        }
        if (isCurrentUser(user)) {
            throw new BusinessException("Bạn không thể tự khóa tài khoản của chính mình");
        }
        if (user.getRole() == UserRole.ADMIN) {
            throw new BusinessException("Không thể khóa tài khoản quản trị viên khác");
        }

        UserStatus oldStatus = user.getStatus();
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);

        syncKeycloakEnabledStatus(user, false, "khóa");

        auditLogService.log(AuditAction.LOCK_USER, "users", String.valueOf(id),
                Map.of("status", oldStatus), Map.of("status", UserStatus.INACTIVE));
    }

    @Override
    @Transactional
    public void unlockUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BusinessException("Tài khoản này hiện đang hoạt động bình thường, không cần mở khóa");
        }
        UserStatus oldStatus = user.getStatus();
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        syncKeycloakEnabledStatus(user, true, "mở khóa");

        auditLogService.log(AuditAction.UNLOCK_USER, "users", String.valueOf(id),
                Map.of("status", oldStatus), Map.of("status", UserStatus.ACTIVE));
    }

    @Override
    @Transactional
    public void updateUserRole(Long userId, UserRole role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Tài khoản người dùng này hiện đang bị khóa hoặc chưa được kích hoạt");
        }
        if (user.getRole() == role) {
            throw new BusinessException("Người dùng đã có vai trò này, không cần cập nhật");
        }
        if (isCurrentUser(user)) {
            throw new BusinessException("Bạn không thể tự thay đổi vai trò của chính mình");
        }
        if (user.getRole() == UserRole.ADMIN) {
            throw new BusinessException("Không thể thay đổi vai trò của tài khoản quản trị viên khác");
        }

        UserRole oldRole = user.getRole();
        user.setRole(role);
        userRepository.save(user);

        try {
            keyCloakAuthClient.updateUserRoles(user.getKeycloakUserId(), List.of(role.name()));
        } catch (Exception e) {
            throw new BusinessException("Đồng bộ vai trò lên hệ thống bảo mật thất bại: " + e.getMessage());
        }

        auditLogService.log(AuditAction.UPDATE_USER_ROLE, "users", String.valueOf(userId),
                Map.of("role", oldRole), Map.of("role", role));
    }

    @Override
    @Transactional(readOnly = true)
    public User getCurrentUser() {
        String keycloakUserId = SecurityUtils.getCurrentUserKeyCloakId().orElseThrow(
                () -> new RuntimeException("Không tìm thấy thông tin người dùng hiện tại")
        );

        User user = userRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Tài khoản của bạn chưa được kích hoạt hoặc đã bị khóa");
        }

        return user;
    }

    @Override
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));
    }

    @Override
    public List<User> getUsersByIds(List<Long> ids) {
        return userRepository.findAllById(ids);
    }

    //Tài khoản PENDING (hoặc dữ liệu lệch với Keycloak) có thể không còn tồn tại bên Keycloak.
    //Khi đó vẫn cho phép admin khóa/mở khóa trong hệ thống thay vì chặn toàn bộ thao tác.
    private void syncKeycloakEnabledStatus(User user, boolean enabled, String actionLabel) {
        String keycloakUserId = user.getKeycloakUserId();
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            log.warn("Bỏ qua đồng bộ {} lên Keycloak: user {} chưa có keycloakUserId", actionLabel, user.getUserId());
            return;
        }
        try {
            keyCloakAuthClient.updateUserEnabledStatus(keycloakUserId, enabled);
        } catch (BusinessException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                log.warn("Bỏ qua đồng bộ {} lên Keycloak: không tìm thấy user {} (keycloakUserId {})",
                        actionLabel, user.getUserId(), keycloakUserId);
                return;
            }
            throw new BusinessException("Đồng bộ trạng thái " + actionLabel
                    + " lên hệ thống bảo mật thất bại: " + e.getMessage());
        } catch (Exception e) {
            throw new BusinessException("Đồng bộ trạng thái " + actionLabel
                    + " lên hệ thống bảo mật thất bại: " + e.getMessage());
        }
    }

    private boolean isCurrentUser(User user) {
        return SecurityUtils.getCurrentUserKeyCloakId()
                .map(keycloakUserId -> keycloakUserId.equals(user.getKeycloakUserId()))
                .orElse(false);
    }

    private UserProfileResponse enrichProfileResponse(User user) {
        UserProfileResponse response = userMapper.toProfileResponse(user);

        long followers = userFollowRepository.countByFollowing(user);
        long following = userFollowRepository.countByFollower(user);
        long posts = postRepository.countByUser(user);

        response.setTotalFollowers(followers);
        response.setTotalFollowing(following);
        response.setTotalPosts(posts);
        return response;
    }

    //isFollowing chỉ có ý nghĩa khi xem profile người khác, nên không tính trong hàm chung
    //(getAllUsersWithFilter map cả trang user, tính ở đó sẽ thành N+1).
    private UserProfileResponse enrichProfileResponse(User user, User viewer) {
        UserProfileResponse response = enrichProfileResponse(user);
        if (viewer != null && !viewer.getUserId().equals(user.getUserId())) {
            response.setIsFollowing(userFollowRepository.existsByFollowerAndFollowing(viewer, user));
        }
        return response;
    }

    //Người xem không resolve được thì để isFollowing null thay vì ném lỗi:
    //các API profile/danh sách vẫn phải trả dữ liệu bình thường.
    private User findCurrentUserOrNull() {
        return SecurityUtils.getCurrentUserKeyCloakId()
                .flatMap(userRepository::findByKeycloakUserId)
                .orElse(null);
    }

    private Set<Long> findFollowedIds(User viewer, List<Long> userIds) {
        if (viewer == null || userIds.isEmpty()) {
            return Set.of();
        }
        return userFollowRepository.findFollowingIdsByFollowerAndFollowingIdIn(viewer, userIds);
    }

    private FollowUserResponse toFollowUserResponse(User user, User viewer, Set<Long> followedIds) {
        return FollowUserResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .levelName(user.getLevel() != null ? user.getLevel().getName() : null)
                .isFollowing(viewer == null ? null : followedIds.contains(user.getUserId()))
                .build();
    }
}
