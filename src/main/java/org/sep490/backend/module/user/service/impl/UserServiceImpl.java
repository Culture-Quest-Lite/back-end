package org.sep490.backend.module.user.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.common.filter.dto.BaseFilterRequest;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.config.keycloak.KeyCloakAuthClient;
import org.sep490.backend.config.redis.CacheNames;
import org.sep490.backend.config.redis.RedisCircuitBreaker;
import org.sep490.backend.common.utils.SecurityUtils;
import org.sep490.backend.module.admin.entity.enumeration.AuditAction;
import org.sep490.backend.module.admin.service.AuditLogService;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.sep490.backend.module.authentication.mapper.UserMapper;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.content.service.inter.ImageService;
import org.sep490.backend.module.notification.entity.enumeration.NotificationType;
import org.sep490.backend.module.notification.service.NotificationService;
import org.sep490.backend.module.social.repository.PostRepository;
import org.sep490.backend.module.user.dto.filter.LeaderboardFilterRequest;
import org.sep490.backend.module.user.dto.request.UpdateProfileRequest;
import org.sep490.backend.module.user.dto.response.FollowStatusResponse;
import org.sep490.backend.module.user.dto.response.FollowUserResponse;
import org.sep490.backend.module.user.dto.response.LeaderboardEntryResponse;
import org.sep490.backend.module.user.dto.response.LeaderboardPageCache;
import org.sep490.backend.module.user.dto.response.MyLeaderboardRankResponse;
import org.sep490.backend.module.user.dto.response.UserProfileResponse;
import org.sep490.backend.module.user.entity.UserFollow;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.sep490.backend.module.user.repository.UserFollowRepository;
import org.sep490.backend.module.user.service.LeaderboardCacheService;
import org.sep490.backend.module.user.service.UserIdCacheService;
import org.sep490.backend.module.user.service.UserService;
import org.sep490.backend.module.user.specification.UserSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Set;

@Slf4j
@Service("userService")
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserFollowRepository userFollowRepository;
    private final KeyCloakAuthClient keyCloakAuthClient;
    private final PostRepository postRepository;
    private final AuditLogService auditLogService;
    private final LeaderboardCacheService leaderboardCacheService;
    private final UserIdCacheService userIdCacheService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisCircuitBreaker circuitBreaker;
    private final NotificationService notificationService;
    private final ImageService imageService;

    private static final String AVATAR_FOLDER = "avatars";
    private static final String BACKGROUND_FOLDER = "backgrounds";

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
        if (request.getAvatarFile() != null && !request.getAvatarFile().isEmpty()) {
            user.setAvatarUrl(imageService.resolveImageUrl(
                    user.getAvatarUrl(), request.getAvatarFile(), AVATAR_FOLDER));
        }
        if (request.getBackgroundFile() != null && !request.getBackgroundFile().isEmpty()) {
            user.setBackgroundUrl(imageService.resolveImageUrl(
                    user.getBackgroundUrl(), request.getBackgroundFile(), BACKGROUND_FOLDER));
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


        if (!userFollowRepository.existsByFollowerAndFollowing(follower, following)) {
            UserFollow userFollow = UserFollow.builder()
                    .follower(follower)
                    .following(following)
                    .build();
            userFollowRepository.save(userFollow);
            evictUserCounters(follower.getUserId());
            evictUserCounters(following.getUserId());
        }

        notificationService.sendAndSave(
                following,
                "Người theo dõi mới",
                follower.getDisplayName() + " đã bắt đầu theo dõi bạn.",
                NotificationType.FOLLOW,
                follower.getUserId()
        );

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
                .ifPresent(userFollow -> {
                    userFollowRepository.delete(userFollow);
                    evictUserCounters(follower.getUserId());
                    evictUserCounters(following.getUserId());
                });

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
    @Transactional(readOnly = true)
    public Page<LeaderboardEntryResponse> getXpLeaderboard(LeaderboardFilterRequest filter) {
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize());

        LeaderboardPageCache cached = leaderboardCacheService.loadPage(
                filter.getPage(), filter.getSize());

        User viewer = findCurrentUserOrNull();

        List<LeaderboardEntryResponse> entries = cached.getEntries();
        entries.forEach(entry -> entry.setIsCurrentUser(
                viewer == null ? null : viewer.getUserId().equals(entry.getUserId())));

        return new PageImpl<>(entries, pageable, cached.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public MyLeaderboardRankResponse getMyXpRank() {
        User me = getCurrentUser();
        if (me.getRole() != UserRole.EXPLORER) {
            throw new BusinessException("Tài khoản của bạn không tham gia bảng xếp hạng");
        }

        int xp = me.getTotalXp() != null ? me.getTotalXp() : 0;
        long above = leaderboardCacheService.countRankedAbove(me.getUserId(), xp, me.getCreatedAt());

        MyLeaderboardRankResponse response = new MyLeaderboardRankResponse();
        response.setEntry(toLeaderboardEntry(me, (int) (above + 1), me));
        response.setTotalParticipants(leaderboardCacheService.countParticipants());
        return response;
    }

    private LeaderboardEntryResponse toLeaderboardEntry(User user, int rank, User viewer) {
        LeaderboardEntryResponse entry = new LeaderboardEntryResponse();
        entry.setRank(rank);
        entry.setUserId(user.getUserId());
        entry.setUsername(user.getUsername());
        entry.setDisplayName(user.getDisplayName());
        entry.setAvatarUrl(user.getAvatarUrl());
        entry.setTotalXp(user.getTotalXp() != null ? user.getTotalXp() : 0);
        entry.setLevelName(user.getLevel() != null ? user.getLevel().getName() : null);
        entry.setIsCurrentUser(viewer == null ? null : viewer.getUserId().equals(user.getUserId()));
        return entry;
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
        userIdCacheService.evict(user.getKeycloakUserId());

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
        userIdCacheService.evict(user.getKeycloakUserId());

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

        Long userId = userIdCacheService.resolveUserId(keycloakUserId);

        User user = (userId != null
                ? userRepository.findById(userId)
                : userRepository.findByKeycloakUserId(keycloakUserId))
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

    @Override
    public List<FollowUserResponse> getFriends(String displayName) {

        User currentUser = getCurrentUser();
        List<User> mutualFollowers = displayName != null && !displayName.trim().isEmpty() ?
                userFollowRepository.findMutualFollowers(currentUser.getUserId(), displayName) :
                userFollowRepository.findMutualFollowers(currentUser.getUserId());

        return mutualFollowers.stream()
                .map(user -> {
                    return FollowUserResponse.builder()
                            .userId(user.getUserId())
                            .username(user.getUsername())
                            .displayName(user.getDisplayName())
                            .avatarUrl(user.getAvatarUrl())
                            .levelName(user.getLevel() != null ? user.getLevel().getName() : null)
                            .build();
                })
                .collect(Collectors.toList());
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
        applyCounters(response, user);
        return response;
    }

    private void applyCounters(UserProfileResponse response, User user) {
        String key = String.format(CacheNames.KEY_USER_COUNTS, user.getUserId());

        Map<Object, Object> cached = circuitBreaker.read("user.counts.get",
                () -> redisTemplate.opsForHash().entries(key), Map.of());

        if (cached != null && cached.size() == 3) {
            response.setTotalFollowers(Long.parseLong(cached.get("followers").toString()));
            response.setTotalFollowing(Long.parseLong(cached.get("following").toString()));
            response.setTotalPosts(Long.parseLong(cached.get("posts").toString()));
            return;
        }

        long followers = userFollowRepository.countByFollowing(user);
        long following = userFollowRepository.countByFollower(user);
        long posts = postRepository.countByUser(user);

        response.setTotalFollowers(followers);
        response.setTotalFollowing(following);
        response.setTotalPosts(posts);

        circuitBreaker.write("user.counts.set", () -> {
            redisTemplate.opsForHash().putAll(key, Map.of(
                    "followers", String.valueOf(followers),
                    "following", String.valueOf(following),
                    "posts", String.valueOf(posts)));
            redisTemplate.expire(key, Duration.ofMinutes(15));
        });
    }

    public void evictUserCounters(Long userId) {
        if (userId == null) {
            return;
        }
        circuitBreaker.write("user.counts.evict",
                () -> redisTemplate.delete(String.format(CacheNames.KEY_USER_COUNTS, userId)));
    }

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
