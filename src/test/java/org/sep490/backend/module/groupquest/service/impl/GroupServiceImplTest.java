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

}