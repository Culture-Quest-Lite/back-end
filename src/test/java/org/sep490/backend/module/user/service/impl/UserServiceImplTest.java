package org.sep490.backend.module.user.service.impl;

import org.sep490.backend.module.user.service.LeaderboardCacheService;
import org.sep490.backend.module.user.service.UserIdCacheService;

import org.springframework.data.redis.core.RedisTemplate;
import org.sep490.backend.config.redis.RedisCircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.config.keycloak.KeyCloakAuthClient;
import org.sep490.backend.module.admin.service.AuditLogService;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.sep490.backend.module.authentication.mapper.UserMapper;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.content.service.inter.ImageService;
import org.sep490.backend.module.social.repository.PostRepository;
import org.sep490.backend.module.user.dto.request.UpdateProfileRequest;
import org.sep490.backend.module.user.dto.response.FollowStatusResponse;
import org.sep490.backend.module.user.dto.response.UserProfileResponse;
import org.sep490.backend.module.user.entity.UserFollow;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.sep490.backend.module.user.repository.UserFollowRepository;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private UserFollowRepository userFollowRepository;
    @Mock private KeyCloakAuthClient keyCloakAuthClient;
    @Mock private PostRepository postRepository;
    @Mock private AuditLogService auditLogService;

    @Mock private LeaderboardCacheService leaderboardCacheService;
    @Mock private UserIdCacheService userIdCacheService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private RedisCircuitBreaker circuitBreaker;
    @Mock private ImageService imageService;
    @InjectMocks private UserServiceImpl userService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** Giả lập người đang đăng nhập có keycloakUserId = {sub}. */
    private void loginAs(String sub) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(sub).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    // =====================================================================
    // Function: updateMyProfile
    // =====================================================================
    @Nested
    @DisplayName("updateMyProfile")
    class UpdateMyProfileTest {

        private UpdateProfileRequest updateRequest(String displayName) {
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setDisplayName(displayName);
            request.setAvatarFile(new MockMultipartFile("avatarFile", "avatar.png", "image/png", new byte[1024]));
            request.setBackgroundFile(new MockMultipartFile("backgroundFile", "bg.png", "image/png", new byte[1024]));
            request.setAutoPlayAudio(false);
            return request;
        }

        private void stubEnrich() {
            when(userMapper.toProfileResponse(any(User.class))).thenReturn(new UserProfileResponse());
            when(userFollowRepository.countByFollowing(any(User.class))).thenReturn(0L);
            when(userFollowRepository.countByFollower(any(User.class))).thenReturn(0L);
            when(postRepository.countByUser(any(User.class))).thenReturn(0L);
            when(imageService.resolveImageUrl(any(), any(), eq("avatars"))).thenReturn("http://img/avatar.png");
            when(imageService.resolveImageUrl(any(), any(), eq("backgrounds"))).thenReturn("http://img/bg.png");
        }

        // UTCID01 - Abnormal: không tìm thấy người dùng
        @Test
        void updateMyProfile_userNotFound_throwsUserNotFound() {
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.updateMyProfile("kc-001", updateRequest("Traveler")));

            assertEquals("Không tìm thấy thông tin người dùng", ex.getMessage());
        }

        // UTCID02 - Abnormal: tài khoản không ở trạng thái hoạt động
        @Test
        void updateMyProfile_inactiveAccount_throwsNotActive() {
            User user = new User();
            user.setStatus(UserStatus.INACTIVE);
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.of(user));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.updateMyProfile("kc-001", updateRequest("Traveler")));

            assertEquals("Tài khoản của bạn không ở trạng thái hoạt động để cập nhật", ex.getMessage());
        }

        // UTCID03 - Normal: cập nhật hồ sơ thành công
        @Test
        void updateMyProfile_validRequest_updatesProfile() {
            User user = new User();
            user.setStatus(UserStatus.ACTIVE);
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            stubEnrich();

            UserProfileResponse response = userService.updateMyProfile("kc-001", updateRequest("Traveler"));

            assertNotNull(response);
            assertEquals("Traveler", user.getDisplayName());
            assertEquals("http://img/avatar.png", user.getAvatarUrl());
            assertFalse(user.getAutoPlayAudio());
            verify(userRepository).save(user);
        }

        // UTCID04 - Boundary: displayName có khoảng trắng thừa -> được cắt bỏ (trim)
        @Test
        void updateMyProfile_displayNameWithSpaces_isTrimmed() {
            User user = new User();
            user.setStatus(UserStatus.ACTIVE);
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            stubEnrich();

            userService.updateMyProfile("kc-001", updateRequest("  Traveler  "));

            assertEquals("Traveler", user.getDisplayName());
        }
    }

    // =====================================================================
    // Function: followUser
    // =====================================================================
    @Nested
    @DisplayName("followUser")
    class FollowUserTest {

        private User activeUser(Long id) {
            User user = new User();
            user.setUserId(id);
            user.setStatus(UserStatus.ACTIVE);
            return user;
        }

        // UTCID01 - Abnormal: không tìm thấy người dùng hiện tại
        @Test
        void followUser_currentUserNotFound_throwsUserNotFound() {
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.followUser("kc-001", 2L));

            assertEquals("Không tìm thấy thông tin người dùng", ex.getMessage());
        }

        // UTCID02 - Abnormal: tài khoản của mình bị khóa/chưa kích hoạt
        @Test
        void followUser_currentUserInactive_throwsAccountLocked() {
            User follower = new User();
            follower.setUserId(1L);
            follower.setStatus(UserStatus.INACTIVE);
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.of(follower));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.followUser("kc-001", 2L));

            assertEquals("Tài khoản của bạn bị khóa hoặc chưa kích hoạt", ex.getMessage());
        }

        // UTCID03 - Abnormal: người cần theo dõi không tồn tại
        @Test
        void followUser_targetNotFound_throwsTargetNotExist() {
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.of(activeUser(1L)));
            when(userRepository.findById(2L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.followUser("kc-001", 2L));

            assertEquals("Người dùng cần theo dõi không tồn tại", ex.getMessage());
        }

        // UTCID04 - Abnormal: người cần theo dõi đang không hoạt động
        @Test
        void followUser_targetInactive_throwsTargetInactive() {
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.of(activeUser(1L)));
            User target = new User();
            target.setUserId(2L);
            target.setStatus(UserStatus.INACTIVE);
            when(userRepository.findById(2L)).thenReturn(Optional.of(target));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.followUser("kc-001", 2L));

            assertEquals("Người dùng này hiện không hoạt động", ex.getMessage());
        }

        // UTCID05 - Abnormal: tự theo dõi chính mình
        @Test
        void followUser_selfFollow_throwsSelfFollow() {
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.of(activeUser(1L)));
            when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser(1L)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.followUser("kc-001", 1L));

            assertEquals("Bạn không thể tự theo dõi chính mình", ex.getMessage());
        }

        // UTCID06 - Normal: đã theo dõi rồi thì idempotent, trả về trạng thái thật
        @Test
        void followUser_alreadyFollowing_returnsFollowingWithoutSaving() {
            User follower = activeUser(1L);
            User target = activeUser(2L);
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.of(follower));
            when(userRepository.findById(2L)).thenReturn(Optional.of(target));
            when(userFollowRepository.existsByFollowerAndFollowing(follower, target)).thenReturn(true);
            when(userFollowRepository.countByFollowing(target)).thenReturn(3L);

            FollowStatusResponse response = userService.followUser("kc-001", 2L);

            assertEquals(2L, response.getUserId());
            assertTrue(response.getIsFollowing());
            assertEquals(3L, response.getTotalFollowers());
            verify(userFollowRepository, never()).save(any());
        }

        // UTCID07 - Normal: theo dõi thành công
        @Test
        void followUser_valid_savesFollow() {
            User follower = activeUser(1L);
            User target = activeUser(2L);
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.of(follower));
            when(userRepository.findById(2L)).thenReturn(Optional.of(target));
            when(userFollowRepository.existsByFollowerAndFollowing(follower, target)).thenReturn(false);
            when(userFollowRepository.countByFollowing(target)).thenReturn(1L);

            FollowStatusResponse response = userService.followUser("kc-001", 2L);

            verify(userFollowRepository).save(any(UserFollow.class));
            assertEquals(2L, response.getUserId());
            assertTrue(response.getIsFollowing());
            assertEquals(1L, response.getTotalFollowers());
        }
    }

    // =====================================================================
    // Function: unfollowUser
    // =====================================================================
    @Nested
    @DisplayName("unfollowUser")
    class UnfollowUserTest {

        private User activeUser(Long id) {
            User user = new User();
            user.setUserId(id);
            user.setStatus(UserStatus.ACTIVE);
            return user;
        }

        // UTCID01 - Abnormal: không tìm thấy người dùng hiện tại
        @Test
        void unfollowUser_currentUserNotFound_throwsUserNotFound() {
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.unfollowUser("kc-001", 2L));

            assertEquals("Không tìm thấy thông tin người dùng", ex.getMessage());
        }

        // UTCID02 - Abnormal: tài khoản của mình bị khóa/chưa kích hoạt
        @Test
        void unfollowUser_currentUserInactive_throwsAccountLocked() {
            User follower = new User();
            follower.setUserId(1L);
            follower.setStatus(UserStatus.INACTIVE);
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.of(follower));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.unfollowUser("kc-001", 2L));

            assertEquals("Tài khoản của bạn bị khóa hoặc chưa kích hoạt", ex.getMessage());
        }

        // UTCID03 - Abnormal: người cần bỏ theo dõi không tồn tại
        @Test
        void unfollowUser_targetNotFound_throwsTargetNotExist() {
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.of(activeUser(1L)));
            when(userRepository.findById(2L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.unfollowUser("kc-001", 2L));

            assertEquals("Người dùng cần bỏ theo dõi không tồn tại", ex.getMessage());
        }

        // UTCID04 - Normal: chưa từng theo dõi thì idempotent, trả về trạng thái thật
        @Test
        void unfollowUser_notFollowing_returnsNotFollowingWithoutDeleting() {
            User follower = activeUser(1L);
            User target = activeUser(2L);
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.of(follower));
            when(userRepository.findById(2L)).thenReturn(Optional.of(target));
            when(userFollowRepository.findByFollowerAndFollowing(follower, target)).thenReturn(Optional.empty());
            when(userFollowRepository.countByFollowing(target)).thenReturn(0L);

            FollowStatusResponse response = userService.unfollowUser("kc-001", 2L);

            assertEquals(2L, response.getUserId());
            assertFalse(response.getIsFollowing());
            assertEquals(0L, response.getTotalFollowers());
            verify(userFollowRepository, never()).delete(any(UserFollow.class));
        }

        // UTCID05 - Normal: bỏ theo dõi thành công
        @Test
        void unfollowUser_valid_deletesFollow() {
            User follower = activeUser(1L);
            User target = activeUser(2L);
            UserFollow follow = UserFollow.builder().follower(follower).following(target).build();
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.of(follower));
            when(userRepository.findById(2L)).thenReturn(Optional.of(target));
            when(userFollowRepository.findByFollowerAndFollowing(follower, target)).thenReturn(Optional.of(follow));
            when(userFollowRepository.countByFollowing(target)).thenReturn(2L);

            FollowStatusResponse response = userService.unfollowUser("kc-001", 2L);

            verify(userFollowRepository).delete(follow);
            assertEquals(2L, response.getUserId());
            assertFalse(response.getIsFollowing());
            assertEquals(2L, response.getTotalFollowers());
        }
    }

    // =====================================================================
    // Function: lockUser
    // =====================================================================
    @Nested
    @DisplayName("lockUser")
    class LockUserTest {

        private User userWith(UserStatus status, UserRole role, String keycloakId) {
            User user = new User();
            user.setUserId(5L);
            user.setStatus(status);
            user.setRole(role);
            user.setKeycloakUserId(keycloakId);
            return user;
        }

        // UTCID01 - Abnormal: không tìm thấy người dùng
        @Test
        void lockUser_notFound_throwsUserNotFound() {
            when(userRepository.findById(5L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.lockUser(5L));

            assertEquals("Không tìm thấy thông tin người dùng", ex.getMessage());
        }

        // UTCID02 - Abnormal: tài khoản đã bị khóa từ trước
        @Test
        void lockUser_alreadyLocked_throwsAlreadyLocked() {
            when(userRepository.findById(5L))
                    .thenReturn(Optional.of(userWith(UserStatus.INACTIVE, UserRole.EXPLORER, "kc-5")));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.lockUser(5L));

            assertEquals("Tài khoản này đã bị khóa từ trước", ex.getMessage());
        }

        // UTCID03 - Abnormal: tự khóa tài khoản của chính mình
        @Test
        void lockUser_selfLock_throwsSelfLock() {
            loginAs("kc-5");
            when(userRepository.findById(5L))
                    .thenReturn(Optional.of(userWith(UserStatus.ACTIVE, UserRole.EXPLORER, "kc-5")));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.lockUser(5L));

            assertEquals("Bạn không thể tự khóa tài khoản của chính mình", ex.getMessage());
        }

        // UTCID04 - Abnormal: khóa tài khoản admin khác
        @Test
        void lockUser_otherAdmin_throwsCannotLockAdmin() {
            when(userRepository.findById(5L))
                    .thenReturn(Optional.of(userWith(UserStatus.ACTIVE, UserRole.ADMIN, "kc-5")));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.lockUser(5L));

            assertEquals("Không thể khóa tài khoản quản trị viên khác", ex.getMessage());
        }

        // UTCID05 - Normal: khóa tài khoản thành công
        @Test
        void lockUser_valid_locksAccount() {
            User user = userWith(UserStatus.ACTIVE, UserRole.EXPLORER, "kc-5");
            when(userRepository.findById(5L)).thenReturn(Optional.of(user));

            userService.lockUser(5L);

            assertEquals(UserStatus.INACTIVE, user.getStatus());
            verify(userRepository).save(user);
            verify(keyCloakAuthClient).updateUserEnabledStatus("kc-5", false);
        }
    }

    // =====================================================================
    // Function: unlockUser
    // =====================================================================
    @Nested
    @DisplayName("unlockUser")
    class UnlockUserTest {

        private User userWith(UserStatus status) {
            User user = new User();
            user.setUserId(5L);
            user.setStatus(status);
            user.setKeycloakUserId("kc-5");
            return user;
        }

        // UTCID01 - Abnormal: không tìm thấy người dùng
        @Test
        void unlockUser_notFound_throwsUserNotFound() {
            when(userRepository.findById(5L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.unlockUser(5L));

            assertEquals("Không tìm thấy thông tin người dùng", ex.getMessage());
        }

        // UTCID02 - Abnormal: tài khoản đang hoạt động, không cần mở khóa
        @Test
        void unlockUser_alreadyActive_throwsAlreadyActive() {
            when(userRepository.findById(5L)).thenReturn(Optional.of(userWith(UserStatus.ACTIVE)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.unlockUser(5L));

            assertEquals("Tài khoản này hiện đang hoạt động bình thường, không cần mở khóa", ex.getMessage());
        }

        // UTCID03 - Normal: mở khóa tài khoản thành công
        @Test
        void unlockUser_valid_unlocksAccount() {
            User user = userWith(UserStatus.INACTIVE);
            when(userRepository.findById(5L)).thenReturn(Optional.of(user));

            userService.unlockUser(5L);

            assertEquals(UserStatus.ACTIVE, user.getStatus());
            verify(userRepository).save(user);
            verify(keyCloakAuthClient).updateUserEnabledStatus("kc-5", true);
        }
    }

    // =====================================================================
    // Function: updateUserRole
    // =====================================================================
    @Nested
    @DisplayName("updateUserRole")
    class UpdateUserRoleTest {

        private User userWith(UserStatus status, UserRole role, String keycloakId) {
            User user = new User();
            user.setUserId(5L);
            user.setStatus(status);
            user.setRole(role);
            user.setKeycloakUserId(keycloakId);
            return user;
        }

        // UTCID01 - Abnormal: không tìm thấy người dùng
        @Test
        void updateUserRole_notFound_throwsUserNotFound() {
            when(userRepository.findById(5L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.updateUserRole(5L, UserRole.CURATOR));

            assertEquals("Không tìm thấy thông tin người dùng", ex.getMessage());
        }

        // UTCID02 - Abnormal: tài khoản bị khóa hoặc chưa kích hoạt
        @Test
        void updateUserRole_inactiveAccount_throwsNotActive() {
            when(userRepository.findById(5L))
                    .thenReturn(Optional.of(userWith(UserStatus.INACTIVE, UserRole.EXPLORER, "kc-5")));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.updateUserRole(5L, UserRole.CURATOR));

            assertEquals("Tài khoản người dùng này hiện đang bị khóa hoặc chưa được kích hoạt", ex.getMessage());
        }

        // UTCID03 - Abnormal: người dùng đã có vai trò này
        @Test
        void updateUserRole_alreadyHasRole_throwsAlreadyHasRole() {
            when(userRepository.findById(5L))
                    .thenReturn(Optional.of(userWith(UserStatus.ACTIVE, UserRole.EXPLORER, "kc-5")));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.updateUserRole(5L, UserRole.EXPLORER));

            assertEquals("Người dùng đã có vai trò này, không cần cập nhật", ex.getMessage());
        }

        // UTCID04 - Abnormal: tự thay đổi vai trò của chính mình
        @Test
        void updateUserRole_selfChange_throwsSelfChange() {
            loginAs("kc-5");
            when(userRepository.findById(5L))
                    .thenReturn(Optional.of(userWith(UserStatus.ACTIVE, UserRole.EXPLORER, "kc-5")));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.updateUserRole(5L, UserRole.ADMIN));

            assertEquals("Bạn không thể tự thay đổi vai trò của chính mình", ex.getMessage());
        }

        // UTCID05 - Abnormal: thay đổi vai trò của admin khác
        @Test
        void updateUserRole_otherAdmin_throwsCannotChangeAdmin() {
            when(userRepository.findById(5L))
                    .thenReturn(Optional.of(userWith(UserStatus.ACTIVE, UserRole.ADMIN, "kc-5")));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.updateUserRole(5L, UserRole.EXPLORER));

            assertEquals("Không thể thay đổi vai trò của tài khoản quản trị viên khác", ex.getMessage());
        }

        // UTCID06 - Abnormal: đồng bộ vai trò lên Keycloak thất bại
        @Test
        void updateUserRole_keycloakSyncFails_throwsSyncError() {
            User user = userWith(UserStatus.ACTIVE, UserRole.EXPLORER, "kc-5");
            when(userRepository.findById(5L)).thenReturn(Optional.of(user));
            doThrow(new RuntimeException("Keycloak down"))
                    .when(keyCloakAuthClient).updateUserRoles(anyString(), any());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.updateUserRole(5L, UserRole.CURATOR));

            assertTrue(ex.getMessage().startsWith("Đồng bộ vai trò lên hệ thống bảo mật thất bại: "));
        }

        // UTCID07 - Normal: cập nhật vai trò thành công
        @Test
        void updateUserRole_valid_updatesRole() {
            User user = userWith(UserStatus.ACTIVE, UserRole.EXPLORER, "kc-5");
            when(userRepository.findById(5L)).thenReturn(Optional.of(user));

            userService.updateUserRole(5L, UserRole.CURATOR);

            assertEquals(UserRole.CURATOR, user.getRole());
            verify(userRepository).save(user);
            verify(keyCloakAuthClient).updateUserRoles("kc-5", List.of("CURATOR"));
        }
    }
}
