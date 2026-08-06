package org.sep490.backend.module.groupquest.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.exception.GroupAuthorizeException;
import org.sep490.backend.common.utils.GroupUtils;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.groupquest.dto.request.GroupRequest;
import org.sep490.backend.module.groupquest.dto.response.GroupResponse;
import org.sep490.backend.module.groupquest.entity.Group;
import org.sep490.backend.module.groupquest.entity.GroupParticipant;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupParticipantAction;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupRole;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupStatus;
import org.sep490.backend.module.groupquest.entity.enumuration.JoinGroupType;
import org.sep490.backend.module.groupquest.mapper.GroupMapper;
import org.sep490.backend.module.groupquest.repository.GroupParticipantRepository;
import org.sep490.backend.module.groupquest.repository.GroupRepository;
import org.sep490.backend.module.groupquest.service.inter.GroupParticipantService;
import org.sep490.backend.module.notification.service.FcmService;
import org.sep490.backend.module.user.repository.UserFollowRepository;
import org.sep490.backend.module.user.service.UserService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupServiceImplTest {

    @InjectMocks
    private GroupServiceImpl groupService;
    @Mock
    private UserService userService;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupParticipantService groupParticipantService;
    @Mock
    private GroupMapper groupMapper;
    @Mock
    private UserFollowRepository userFollowRepository;
    @Mock
    private FcmService fcmService;
    @Mock
    private GroupParticipantRepository groupParticipantRepository;

    private final Long GROUP_ID = 100L;

    @Nested
    @DisplayName("createGroup")
    class CreateGroupTest {

        private GroupRequest createMockRequest(List<Long> userIds) {
            GroupRequest request = new GroupRequest();
            request.setGroupName("Test Group");
            request.setUserIds(userIds);
            return request;
        }

        private User createMockUser(Long id) {
            User user = new User();
            user.setUserId(id);
            return user;
        }

        @Test
        @DisplayName("UTCID 1: Abnormal - No mutual followers found")
        void createGroup_UTCID1_NoMutualFollowers_ThrowsException() {
            GroupRequest request = createMockRequest(List.of(2L, 3L));
            User leader = createMockUser(1L);

            when(userService.getCurrentUser()).thenReturn(leader);

            when(userFollowRepository.findMutualFollowerIds(leader.getUserId(), request.getUserIds()))
                    .thenReturn(Collections.emptyList());

            BusinessException exception = assertThrows(BusinessException.class, () -> {
                groupService.createGroup(request);
            });

            assertEquals("Bạn và 1 thành viên cần phải theo dõi nhau để tạo nhóm", exception.getMessage());

            verify(groupRepository, never()).save(any(Group.class));
        }

        @Test
        @DisplayName("UTCID 2: Normal - Partial mutual followers (Leader is announced)")
        void createGroup_UTCID2_PartialMutualFollowers_Success() {

            GroupRequest request = createMockRequest(List.of(2L, 3L));
            request.setGroupName("Test Group");

            User leader = createMockUser(1L);
            leader.setFcmToken("mock-fcm-token");

            User validMember = createMockUser(2L);
            validMember.setUsername("user2");

            User invalidMember = createMockUser(3L);
            invalidMember.setUsername("user3");

            Group savedGroup = new Group();
            savedGroup.setGroupId(100L);

            when(userService.getCurrentUser()).thenReturn(leader);

            when(userFollowRepository.findMutualFollowerIds(leader.getUserId(), request.getUserIds()))
                    .thenReturn(List.of(2L));

            when(userService.getUserById(leader.getUserId())).thenReturn(leader);
            when(userService.getUsersByIds(List.of(3L))).thenReturn(List.of(invalidMember));
            when(userService.getUsersByIds(List.of(2L))).thenReturn(List.of(validMember));
            when(groupRepository.save(any(Group.class))).thenReturn(savedGroup);
            when(groupMapper.toResponse(any(Group.class), any())).thenReturn(new GroupResponse());

            GroupResponse response = groupService.createGroup(request);

            assertNotNull(response);

            verify(fcmService, times(1)).sendPushNotification(
                    eq("mock-fcm-token"),
                    contains("Không thể add 1 thành viên vào nhóm Test Group"),
                    contains("user3, "),
                    any(),
                    eq(1L)
            );

            verify(groupRepository, times(2)).save(any(Group.class));

            verify(groupParticipantService, times(1)).addUsersToGroup(eq(List.of(validMember)), any(Group.class));
        }

        @Test
        @DisplayName("UTCID 3: Normal - All members are mutual followers")
        void createGroup_UTCID3_AllMutualFollowers_Success() {

            GroupRequest request = createMockRequest(List.of(2L, 3L));
            User leader = createMockUser(1L);
            User member1 = createMockUser(2L);
            User member2 = createMockUser(3L);

            Group savedGroup = new Group();
            savedGroup.setGroupId(100L);

            when(userService.getCurrentUser()).thenReturn(leader);

            when(userFollowRepository.findMutualFollowerIds(leader.getUserId(), request.getUserIds()))
                    .thenReturn(List.of(2L, 3L));

            when(userService.getUsersByIds(List.of(2L, 3L))).thenReturn(List.of(member1, member2));
            when(groupRepository.save(any(Group.class))).thenReturn(savedGroup);
            when(groupMapper.toResponse(any(Group.class), any())).thenReturn(new GroupResponse());

            GroupResponse response = groupService.createGroup(request);

            assertNotNull(response);
            verify(groupRepository, times(2)).save(any(Group.class));
            verify(groupParticipantService, times(1)).addUsersToGroup(eq(List.of(member1, member2)), any(Group.class));
        }
    }

    @Nested
    @DisplayName("joinGroup")
    class JoinGroupTest {

        private final String VALID_TOKEN = "valid-share-token-123";

        // Hàm tiện ích tạo Mock User
        private User createMockUser(Long id) {
            User user = new User();
            user.setUserId(id);
            return user;
        }

        // Hàm tiện ích tạo Mock Group mặc định hợp lệ
        private Group createValidGroup() {
            Group group = new Group();
            group.setGroupId(GROUP_ID);
            group.setStatus(GroupStatus.ACTIVE);
            group.setShareToken(VALID_TOKEN);
            group.setExpireAt(LocalDateTime.now().plusDays(1)); // Còn hạn 1 ngày
            return group;
        }

        @Test
        @DisplayName("UTCID 1: Abnormal - Group status is DELETED")
        void joinGroup_UTCID1_GroupDeleted_ThrowsException() {
            User user = createMockUser(2L);
            Group deletedGroup = createValidGroup();
            deletedGroup.setStatus(GroupStatus.DELETED); // Chuyển trạng thái sang DELETED

            when(userService.getCurrentUser()).thenReturn(user);

            // Giả định getGroup(groupId) sẽ gọi hàm findById
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(deletedGroup));

            // Mock Static phương thức của GroupUtils
            try (MockedStatic<GroupUtils> mockedStatic = mockStatic(GroupUtils.class)) {
                GroupUtils.TokenInfo mockTokenInfo = mock(GroupUtils.TokenInfo.class);
                when(mockTokenInfo.groupId()).thenReturn(GROUP_ID);
                mockedStatic.when(() -> GroupUtils.parseToken(VALID_TOKEN)).thenReturn(mockTokenInfo);

                BusinessException exception = assertThrows(BusinessException.class, () -> {
                    groupService.joinGroup(VALID_TOKEN);
                });

                assertEquals("Nhóm đã bị xóa", exception.getMessage());

                // Đảm bảo user KHÔNG được add vào nhóm
                verify(groupParticipantService, never()).addUserToGroup(any(), any(), any());
            }
        }

        @Test
        @DisplayName("UTCID 2: Abnormal - Token does not match")
        void joinGroup_UTCID2_TokenInvalid_ThrowsException() {
            User user = createMockUser(2L);
            Group group = createValidGroup();
            group.setShareToken("different-token-in-db"); // Token trong DB khác với token gửi lên

            when(userService.getCurrentUser()).thenReturn(user);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

            try (MockedStatic<GroupUtils> mockedStatic = mockStatic(GroupUtils.class)) {
                GroupUtils.TokenInfo mockTokenInfo = mock(GroupUtils.TokenInfo.class);
                when(mockTokenInfo.groupId()).thenReturn(GROUP_ID);
                mockedStatic.when(() -> GroupUtils.parseToken(VALID_TOKEN)).thenReturn(mockTokenInfo);

                BusinessException exception = assertThrows(BusinessException.class, () -> {
                    groupService.joinGroup(VALID_TOKEN);
                });

                assertEquals("Token không hợp lệ", exception.getMessage());
                verify(groupParticipantService, never()).addUserToGroup(any(), any(), any());
            }
        }

        @Test
        @DisplayName("UTCID 3: Abnormal - Token is expired")
        void joinGroup_UTCID3_TokenExpired_ThrowsException() {
            User user = createMockUser(2L);
            Group group = createValidGroup();
            // Cài đặt ngày hết hạn là 1 ngày TRƯỚC (đã hết hạn)
            group.setExpireAt(LocalDateTime.now().minusDays(1));

            when(userService.getCurrentUser()).thenReturn(user);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

            try (MockedStatic<GroupUtils> mockedStatic = mockStatic(GroupUtils.class)) {
                GroupUtils.TokenInfo mockTokenInfo = mock(GroupUtils.TokenInfo.class);
                when(mockTokenInfo.groupId()).thenReturn(GROUP_ID);
                mockedStatic.when(() -> GroupUtils.parseToken(VALID_TOKEN)).thenReturn(mockTokenInfo);

                BusinessException exception = assertThrows(BusinessException.class, () -> {
                    groupService.joinGroup(VALID_TOKEN);
                });

                assertEquals("Token đã hết hạn", exception.getMessage());
                verify(groupParticipantService, never()).addUserToGroup(any(), any(), any());
            }
        }

        @Test
        @DisplayName("UTCID 4: Normal - Join group successfully")
        void joinGroup_UTCID4_Success() {
            User user = createMockUser(2L);
            Group group = createValidGroup();

            // Chuẩn bị Mock Data cho việc lấy Leader ở cuối hàm
            GroupParticipant mockLeaderParticipant = new GroupParticipant();
            mockLeaderParticipant.setUser(createMockUser(1L)); // ID của leader là 1

            when(userService.getCurrentUser()).thenReturn(user);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

            // Tránh NullPointerException cho hàm getLeaderFromGroup
            when(groupParticipantRepository.findByGroup_GroupIdAndRole(GROUP_ID, GroupRole.LEADER))
                    .thenReturn(mockLeaderParticipant);

            when(groupMapper.toResponse(any(Group.class), eq(1L))).thenReturn(new GroupResponse());

            try (MockedStatic<GroupUtils> mockedStatic = mockStatic(GroupUtils.class)) {
                GroupUtils.TokenInfo mockTokenInfo = mock(GroupUtils.TokenInfo.class);
                when(mockTokenInfo.groupId()).thenReturn(GROUP_ID);
                mockedStatic.when(() -> GroupUtils.parseToken(VALID_TOKEN)).thenReturn(mockTokenInfo);

                // Act
                GroupResponse response = groupService.joinGroup(VALID_TOKEN);

                // Assert
                assertNotNull(response);

                // Verify rằng hàm thêm user vào nhóm đã được gọi đúng 1 lần với type là LINK
                verify(groupParticipantService, times(1)).addUserToGroup(eq(user), eq(group), eq(JoinGroupType.LINK));
            }
        }
    }

    @Nested
    @DisplayName("leaveGroup")
    class LeaveGroupTest {

        private final Long USER_ID = 2L;

        private User createMockUser(Long id) {
            User user = new User();
            user.setUserId(id);
            return user;
        }

        private Group createMockGroup(GroupStatus status) {
            Group group = new Group();
            group.setGroupId(GROUP_ID);
            group.setStatus(status);
            return group;
        }

        @Test
        @DisplayName("UTCID 1: Abnormal - Group is DELETED")
        void leaveGroup_UTCID1_GroupDeleted_ThrowsException() {
            User user = createMockUser(USER_ID);
            Group group = createMockGroup(GroupStatus.DELETED);

            when(userService.getCurrentUser()).thenReturn(user);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

            BusinessException exception = assertThrows(BusinessException.class, () -> {
                groupService.leaveGroup(GROUP_ID);
            });

            assertEquals("Nhóm đã bị xóa", exception.getMessage());
            verify(groupParticipantService, never()).updateAction(any(), any(), any());
        }

        @Test
        @DisplayName("UTCID 2: Abnormal - User is PENDING")
        void leaveGroup_UTCID2_UserPending_ThrowsException() {
            User user = createMockUser(USER_ID);
            Group group = createMockGroup(GroupStatus.ACTIVE);

            when(userService.getCurrentUser()).thenReturn(user);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserId_AndAction(GROUP_ID, USER_ID, GroupParticipantAction.PENDING)).thenReturn(true);

            BusinessException exception = assertThrows(BusinessException.class, () -> {
                groupService.leaveGroup(GROUP_ID);
            });

            assertEquals("Bạn không thể rời nhóm khi đang chờ duyệt. Hãy hủy yêu cầu tham gia nhóm", exception.getMessage());
            verify(groupParticipantService, never()).updateAction(any(), any(), any());
        }

        @Test
        @DisplayName("UTCID 3: Abnormal - User is NOT in group (!JOIN)")
        void leaveGroup_UTCID3_UserNotInGroup_ThrowsException() {
            User user = createMockUser(USER_ID);
            Group group = createMockGroup(GroupStatus.ACTIVE);

            when(userService.getCurrentUser()).thenReturn(user);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserId_AndAction(GROUP_ID, USER_ID, GroupParticipantAction.PENDING)).thenReturn(false);
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserId_AndAction(GROUP_ID, USER_ID, GroupParticipantAction.JOIN)).thenReturn(false);

            BusinessException exception = assertThrows(BusinessException.class, () -> {
                groupService.leaveGroup(GROUP_ID);
            });

            assertEquals("Bạn không thuộc nhóm này", exception.getMessage());
            verify(groupParticipantService, never()).updateAction(any(), any(), any());
        }

        @Test
        @DisplayName("UTCID 4: Abnormal - User is Leader")
        void leaveGroup_UTCID4_UserIsLeader_ThrowsException() {
            User user = createMockUser(USER_ID);
            Group group = createMockGroup(GroupStatus.ACTIVE);

            when(userService.getCurrentUser()).thenReturn(user);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserId_AndAction(GROUP_ID, USER_ID, GroupParticipantAction.PENDING)).thenReturn(false);
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserId_AndAction(GROUP_ID, USER_ID, GroupParticipantAction.JOIN)).thenReturn(true);
            when(groupParticipantService.isLeader(user, group)).thenReturn(true);

            BusinessException exception = assertThrows(BusinessException.class, () -> {
                groupService.leaveGroup(GROUP_ID);
            });

            assertEquals("Trưởng nhóm không thể rời nhóm. Hãy chuyển quyền trưởng nhóm cho người khác trước khi rời nhóm", exception.getMessage());
            verify(groupParticipantService, never()).updateAction(any(), any(), any());
        }

        @Test
        @DisplayName("UTCID 5: Normal - Leave group successfully")
        void leaveGroup_UTCID5_Success() {
            User user = createMockUser(USER_ID);
            Group group = createMockGroup(GroupStatus.ACTIVE);

            GroupParticipant mockLeaderParticipant = new GroupParticipant();
            mockLeaderParticipant.setUser(createMockUser(1L));

            when(userService.getCurrentUser()).thenReturn(user);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserId_AndAction(GROUP_ID, USER_ID, GroupParticipantAction.PENDING)).thenReturn(false);
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserId_AndAction(GROUP_ID, USER_ID, GroupParticipantAction.JOIN)).thenReturn(true);
            when(groupParticipantService.isLeader(user, group)).thenReturn(false);

            when(groupParticipantRepository.findByGroup_GroupIdAndRole(GROUP_ID, GroupRole.LEADER)).thenReturn(mockLeaderParticipant);
            when(groupMapper.toResponse(any(Group.class), any())).thenReturn(new GroupResponse());

            GroupResponse response = groupService.leaveGroup(GROUP_ID);

            assertNotNull(response);
            verify(groupParticipantService, times(1)).updateAction(eq(user), eq(group), eq(GroupParticipantAction.LEAVE));
            verify(groupRepository, times(1)).save(any(Group.class));
        }
    }

    @Nested
    @DisplayName("kickUserFromGroup")
    class KickUserFromGroupTest {

        private Group createGroup(Long id, GroupStatus status) {
            Group group = new Group();
            group.setGroupId(id);
            group.setStatus(status);
            return group;
        }

        private User createUser(Long id) {
            User user = new User();
            user.setUserId(id);
            return user;
        }

        private List<GroupParticipant> createGroupParticipants(int count) {
            List<GroupParticipant> groupParticipants = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                GroupParticipant groupParticipant = new GroupParticipant();
                groupParticipants.add(groupParticipant);
            }
            return groupParticipants;
        }

        // =====================================================================
        // UTCID01 - Abnormal: Nhóm đã bị xóa
        // =====================================================================
        @Test
        void kickUserFromGroup_groupDeleted_throwsException() {
            Long groupId = 1L;
            Long targetUserId = 2L;

            Group group = createGroup(groupId, GroupStatus.DELETED);
            User leader = createUser(1L);
            User member = createUser(targetUserId);

            // Giả lập hàm getGroup() lấy dữ liệu từ repository
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(userService.getCurrentUser()).thenReturn(leader);
            when(userService.getUserById(targetUserId)).thenReturn(member);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.kickUserFromGroup(targetUserId, groupId));

            assertEquals("Nhóm đã bị xóa", ex.getMessage());
        }

        // =====================================================================
        // UTCID02 - Abnormal: Người thực hiện không phải là trưởng nhóm
        // =====================================================================
        @Test
        void kickUserFromGroup_notLeader_throwsAuthorizeException() {
            Long groupId = 1L;
            Long targetUserId = 2L;

            Group group = createGroup(groupId, GroupStatus.ACTIVE);
            User currentUser = createUser(1L); // User hiện tại
            User member = createUser(targetUserId);

            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(userService.getCurrentUser()).thenReturn(currentUser);
            when(userService.getUserById(targetUserId)).thenReturn(member);

            // Bị chặn vì không phải leader
            when(groupParticipantService.isLeader(currentUser, group)).thenReturn(false);

            GroupAuthorizeException ex = assertThrows(GroupAuthorizeException.class,
                    () -> groupService.kickUserFromGroup(targetUserId, groupId));

            assertEquals("Chỉ có trưởng nhóm mới có thể kick thành viên", ex.getMessage());
        }

        // =====================================================================
        // UTCID03 - Abnormal: Tự kick chính mình
        // =====================================================================
        @Test
        void kickUserFromGroup_kickYourself_throwsException() {
            Long groupId = 1L;
            Long targetUserId = 1L; // ID trùng với leader

            Group group = createGroup(groupId, GroupStatus.ACTIVE);
            User leader = createUser(1L);
            User member = createUser(targetUserId); // Chính là leader

            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(userService.getCurrentUser()).thenReturn(leader);
            when(userService.getUserById(targetUserId)).thenReturn(member);
            when(groupParticipantService.isLeader(leader, group)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.kickUserFromGroup(targetUserId, groupId));

            assertEquals("Bạn không thể kick chính mình", ex.getMessage());
        }

        // =====================================================================
        // UTCID04 - Abnormal: User bị kick không có trong nhóm
        // =====================================================================
        @Test
        void kickUserFromGroup_targetNotAMember_throwsException() {
            Long groupId = 1L;
            Long targetUserId = 2L;

            Group group = createGroup(groupId, GroupStatus.ACTIVE);
            User leader = createUser(1L);
            User member = createUser(targetUserId);

            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(userService.getCurrentUser()).thenReturn(leader);
            when(userService.getUserById(targetUserId)).thenReturn(member);
            when(groupParticipantService.isLeader(leader, group)).thenReturn(true);

            // Trả về false -> Bị chặn
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserId_AndAction(
                    groupId, targetUserId, GroupParticipantAction.JOIN)).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.kickUserFromGroup(targetUserId, groupId));

            assertEquals("Thành viên này không thuộc nhóm", ex.getMessage());
        }

        // =====================================================================
        // UTCID05 - Normal: Luồng hợp lệ (Kick thành công)
        // =====================================================================
        @Test
        void kickUserFromGroup_valid_kicksUserAndReturnsResponse() {
            Long groupId = 1L;
            Long targetUserId = 2L;
            Long leaderId = 10L;

            Group group = new Group();
            group.setGroupId(groupId);
            group.setStatus(GroupStatus.ACTIVE);

            User leader = new User();
            leader.setUserId(leaderId);

            User member = new User();
            member.setUserId(targetUserId);

            GroupParticipant leaderParticipant = new GroupParticipant();
            leaderParticipant.setUser(leader);

            List<GroupParticipant> remainingMembers = List.of(
                    new GroupParticipant(),
                    new GroupParticipant(),
                    new GroupParticipant()
            );

            GroupResponse expectedResponse = new GroupResponse();

            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

            when(userService.getCurrentUser()).thenReturn(leader);
            when(userService.getUserById(targetUserId)).thenReturn(member);

            when(groupParticipantService.isLeader(leader, group)).thenReturn(true);
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserId_AndAction(
                    groupId, targetUserId, GroupParticipantAction.JOIN)).thenReturn(true);

            when(groupParticipantRepository.findAllByGroup_GroupIdAndAction(groupId, GroupParticipantAction.JOIN))
                    .thenReturn(remainingMembers);

            when(groupParticipantRepository.findByGroup_GroupIdAndRole(groupId, GroupRole.LEADER))
                    .thenReturn(leaderParticipant);

            when(groupMapper.toResponse(group, leaderId)).thenReturn(expectedResponse);

            GroupResponse actualResponse = groupService.kickUserFromGroup(targetUserId, groupId);

            assertNotNull(actualResponse);
            verify(groupParticipantService).updateAction(member, group, GroupParticipantAction.KICKED);
            verify(groupRepository).save(argThat(savedGroup -> savedGroup.getTotalMembers() == 3));
        }
    }

    @Nested
    @DisplayName("refreshSharedToken")
    class RefreshSharedTokenTest {

        private Group createGroup(Long id, GroupStatus status, String shareToken) {
            Group group = new Group();
            group.setGroupId(id);
            group.setStatus(status);
            group.setShareToken(shareToken);
            return group;
        }

        private User createUser(Long id) {
            User user = new User();
            user.setUserId(id);
            return user;
        }

        private GroupParticipant createParticipant(User user, GroupRole role) {
            GroupParticipant gp = new GroupParticipant();
            gp.setUser(user);
            gp.setRole(role);
            return gp;
        }

        // =====================================================================
        // UTCID01 - Abnormal: Nhóm đã bị xóa
        // =====================================================================
        @Test
        void refreshSharedToken_groupDeleted_throwsException() {
            Long groupId = 1L;
            User user = createUser(1L);
            Group group = createGroup(groupId, GroupStatus.DELETED, "OLD_TOKEN");

            // Mock trả về user và group
            when(userService.getCurrentUser()).thenReturn(user);
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

            // Hàm sẽ query participant trước khi check DELETED
            when(groupParticipantRepository.findByGroup_GroupIdAndUser_UserId(groupId, user.getUserId()))
                    .thenReturn(Optional.of(createParticipant(user, GroupRole.LEADER)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.refreshSharedToken(groupId));

            assertEquals("Nhóm đã bị xóa", ex.getMessage());
        }

        // =====================================================================
        // UTCID02 - Abnormal: Người dùng không phải là trưởng nhóm
        // =====================================================================
        @Test
        void refreshSharedToken_notLeader_throwsAuthorizeException() {
            Long groupId = 1L;
            User user = createUser(2L); // User hiện tại
            Group group = createGroup(groupId, GroupStatus.ACTIVE, "OLD_TOKEN");

            when(userService.getCurrentUser()).thenReturn(user);
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

            // Giả lập user chỉ là MEMBER (hoặc có thể mock trả về Optional.empty() nếu user không trong nhóm)
            when(groupParticipantRepository.findByGroup_GroupIdAndUser_UserId(groupId, user.getUserId()))
                    .thenReturn(Optional.of(createParticipant(user, GroupRole.MEMBER)));

            GroupAuthorizeException ex = assertThrows(GroupAuthorizeException.class,
                    () -> groupService.refreshSharedToken(groupId));

            assertEquals("Chỉ có trưởng nhóm mới có thể tạo mới invite code", ex.getMessage());
        }

        // =====================================================================
        // UTCID03 - Abnormal: Token mới sinh ra bị trùng với Token cũ
        // =====================================================================
        @Test
        void refreshSharedToken_newCodeMatchesOldCode_throwsException() {
            Long groupId = 1L;
            String oldToken = "DUPLICATED_TOKEN";
            User user = createUser(1L);
            Group group = createGroup(groupId, GroupStatus.ACTIVE, oldToken);

            when(userService.getCurrentUser()).thenReturn(user);
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(groupParticipantRepository.findByGroup_GroupIdAndUser_UserId(groupId, user.getUserId()))
                    .thenReturn(Optional.of(createParticipant(user, GroupRole.LEADER)));

            // Sử dụng MockedStatic để ép hàm GroupUtils.generateToken trả về đúng chuỗi token cũ
            try (MockedStatic<GroupUtils> mockedGroupUtils = mockStatic(GroupUtils.class)) {
                mockedGroupUtils.when(() -> GroupUtils.generateToken(groupId)).thenReturn(oldToken);

                BusinessException ex = assertThrows(BusinessException.class,
                        () -> groupService.refreshSharedToken(groupId));

                assertEquals("Gặp lỗi khi generate lại token. Hãy thử lại", ex.getMessage());
            }
        }

        // =====================================================================
        // UTCID04 - Normal: Reset token thành công
        // =====================================================================
        @Test
        void refreshSharedToken_valid_updatesAndReturnsResponse() {
            Long groupId = 1L;
            String oldToken = "OLD_TOKEN";
            String newToken = "NEW_FRESH_TOKEN";
            User leader = createUser(1L);
            Group group = createGroup(groupId, GroupStatus.ACTIVE, oldToken);
            GroupParticipant leaderGp = createParticipant(leader, GroupRole.LEADER);

            // 1. Mock thông tin cơ bản
            when(userService.getCurrentUser()).thenReturn(leader);
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(groupParticipantRepository.findByGroup_GroupIdAndUser_UserId(groupId, leader.getUserId()))
                    .thenReturn(Optional.of(leaderGp));

            // 2. Mock hàm tính leaderId bên trong (getLeaderFromGroup)
            when(groupParticipantRepository.findByGroup_GroupIdAndRole(groupId, GroupRole.LEADER))
                    .thenReturn(leaderGp);

            // 3. Mock Mapper
            GroupResponse expectedResponse = new GroupResponse();
            when(groupMapper.toResponse(any(Group.class), eq(leader.getUserId()))).thenReturn(expectedResponse);

            // 4. Bọc hàm execute bên trong MockedStatic
            try (MockedStatic<GroupUtils> mockedGroupUtils = mockStatic(GroupUtils.class)) {
                mockedGroupUtils.when(() -> GroupUtils.generateToken(groupId)).thenReturn(newToken);

                GroupResponse actualResponse = groupService.refreshSharedToken(groupId);

                // Kiểm tra kết quả trả về
                assertNotNull(actualResponse);

                // Đảm bảo token mới và hạn sử dụng đã được set vào object group trước khi lưu
                verify(groupRepository).save(argThat(savedGroup ->
                        newToken.equals(savedGroup.getShareToken()) &&
                                savedGroup.getExpireAt() != null
                ));
            }
        }
    }

    @Nested
    @DisplayName("addUserToGroup")
    class AddUserToGroupTest {

        private Group createGroup(Long id, GroupStatus status) {
            Group group = new Group();
            group.setGroupId(id);
            group.setStatus(status);
            return group;
        }

        private User createUser(Long id) {
            User user = new User();
            user.setUserId(id);
            return user;
        }

        // =====================================================================
        // UTCID01 - Abnormal: Nhóm đã bị xóa
        // =====================================================================
        @Test
        void addUserToGroup_groupDeleted_throwsException() {
            Long groupId = 1L;
            Long targetUserId = 2L;

            Group group = createGroup(groupId, GroupStatus.DELETED);
            User leader = createUser(1L);
            User targetUser = createUser(targetUserId);

            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(userService.getCurrentUser()).thenReturn(leader);
            when(userService.getUserById(targetUserId)).thenReturn(targetUser);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.addUserToGroup(targetUserId, groupId));

            assertEquals("Không thể add thành viên khi nhóm đã bị xóa", ex.getMessage());
        }

        // =====================================================================
        // UTCID02 - Abnormal: Người thực hiện không phải là trưởng nhóm
        // =====================================================================
        @Test
        void addUserToGroup_notLeader_throwsAuthorizeException() {
            Long groupId = 1L;
            Long targetUserId = 2L;

            Group group = createGroup(groupId, GroupStatus.ACTIVE);
            User currentUser = createUser(3L); // Một user bình thường, không phải leader
            User targetUser = createUser(targetUserId);

            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(userService.getCurrentUser()).thenReturn(currentUser);
            when(userService.getUserById(targetUserId)).thenReturn(targetUser);

            // Giả lập logic của hàm isLeader() trả về false
            // Tùy vào logic thật của hàm này, bạn có thể phải thay đổi Mock cho phù hợp
            when(groupService.isLeader(currentUser.getUserId(), groupId)).thenReturn(false);

            GroupAuthorizeException ex = assertThrows(GroupAuthorizeException.class,
                    () -> groupService.addUserToGroup(targetUserId, groupId));

            assertEquals("Chỉ có trưởng nhóm mới có thể add thành viên", ex.getMessage());
        }

        // =====================================================================
        // UTCID03 - Abnormal: Tự add chính mình
        // =====================================================================
        @Test
        void addUserToGroup_addYourself_throwsException() {
            Long groupId = 1L;
            Long targetUserId = 1L; // Trùng với ID của Leader

            Group group = createGroup(groupId, GroupStatus.ACTIVE);
            User leader = createUser(targetUserId);

            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(userService.getCurrentUser()).thenReturn(leader);
            when(userService.getUserById(targetUserId)).thenReturn(leader); // Trả về chính leader

            when(groupService.isLeader(leader.getUserId(), groupId)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.addUserToGroup(leader.getUserId(), groupId));

            assertEquals("Không thể add chính mình vào nhóm", ex.getMessage());
        }

        // =====================================================================
        // UTCID04 - Abnormal: Hai người chưa follow nhau
        // =====================================================================
        @Test
        void addUserToGroup_notFollowingEachOther_throwsException() {
            Long groupId = 1L;
            Long targetUserId = 2L;

            Group group = createGroup(groupId, GroupStatus.ACTIVE);
            User leader = createUser(1L);
            User targetUser = createUser(targetUserId);

            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(userService.getCurrentUser()).thenReturn(leader);
            when(userService.getUserById(targetUserId)).thenReturn(targetUser);

            when(groupService.isLeader(leader.getUserId(), groupId)).thenReturn(true);

            when(userFollowRepository.existsByFollowerAndFollowing(leader, targetUser)).thenReturn(true);

            when(userFollowRepository.existsByFollowerAndFollowing(targetUser, leader)).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.addUserToGroup(targetUserId, groupId));

            assertEquals("Cả 2 phải theo dõi nhau để add vào group", ex.getMessage());
        }

        // =====================================================================
        // UTCID05 - Normal: Add thành công
        // =====================================================================
        @Test
        void addUserToGroup_valid_addsUserAndReturnsResponse() {
            Long groupId = 1L;
            Long targetUserId = 2L;

            Group group = createGroup(groupId, GroupStatus.ACTIVE);
            User leader = createUser(1L);
            User targetUser = createUser(targetUserId);

            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(userService.getCurrentUser()).thenReturn(leader);
            when(userService.getUserById(targetUserId)).thenReturn(targetUser);

            when(groupService.isLeader(leader.getUserId(), groupId)).thenReturn(true);

            // Giả lập 2 người CÓ follow nhau
            when(userFollowRepository.existsByFollowerAndFollowing(leader, targetUser)).thenReturn(true);

            when(userFollowRepository.existsByFollowerAndFollowing(targetUser, leader)).thenReturn(true);

            // Giả lập cho hàm getLeaderFromGroup(groupId) bên trong
            GroupParticipant leaderGp = new GroupParticipant();
            leaderGp.setUser(leader);
            when(groupParticipantRepository.findByGroup_GroupIdAndRole(groupId, GroupRole.LEADER))
                    .thenReturn(leaderGp);

            // Mock trả về DTO
            GroupResponse expectedResponse = new GroupResponse();
            when(groupMapper.toResponse(group, leader.getUserId())).thenReturn(expectedResponse);

            // Thực thi hành động
            GroupResponse actualResponse = groupService.addUserToGroup(targetUserId, groupId);

            // Kiểm tra kết quả
            assertNotNull(actualResponse);

            // Đảm bảo service add user đã được gọi đúng tham số
            verify(groupParticipantService).addUserToGroup(targetUser, group, JoinGroupType.ADD);
        }
    }

}