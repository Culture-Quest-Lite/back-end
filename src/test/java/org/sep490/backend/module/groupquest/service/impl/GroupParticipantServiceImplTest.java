package org.sep490.backend.module.groupquest.service.impl;

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
import org.sep490.backend.common.exception.GroupAuthorizeException;
import org.sep490.backend.common.exception.GroupConflictException;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.groupquest.entity.Group;
import org.sep490.backend.module.groupquest.entity.GroupParticipant;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupParticipantAction;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupRole;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupStatus;
import org.sep490.backend.module.groupquest.entity.enumuration.JoinGroupType;
import org.sep490.backend.module.groupquest.mapper.GroupParticipantMapper;
import org.sep490.backend.module.groupquest.repository.GroupParticipantRepository;
import org.sep490.backend.module.groupquest.repository.GroupRepository;
import org.sep490.backend.module.user.service.UserService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho THÀNH VIÊN NHÓM (duyệt / từ chối / kick / tham gia).
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupParticipantServiceImplTest {

    @Mock private GroupParticipantRepository repository;
    @Mock private GroupRepository groupRepository;
    @Mock private UserService userService;
    @Mock private GroupParticipantMapper mapper;

    @InjectMocks private GroupParticipantServiceImpl groupParticipantService;

    private static User user(Long userId) {
        User user = new User();
        user.setUserId(userId);
        user.setUsername(userId == 1L ? "leader" : "member");
        user.setDisplayName(userId == 1L ? "Tran Minh Anh" : "Minh Anh");
        user.setEmail((userId == 1L ? "leader" : "member") + "@gmail.com");
        return user;
    }

    private static Group group(Long groupId, boolean requiredApproval) {
        return Group.builder()
                .groupId(groupId)
                .groupName("Nhóm phượt Đà Lạt")
                .totalMembers(3)
                .status(GroupStatus.ACTIVE)
                .requiredApproval(requiredApproval)
                .build();
    }

    private static GroupParticipant participant(Group group, User user,
                                               GroupRole role, GroupParticipantAction action) {
        return GroupParticipant.builder()
                .groupParticipantId(10L)
                .group(group)
                .user(user)
                .role(role)
                .action(action)
                .status(GroupStatus.ACTIVE)
                .build();
    }

    // =====================================================================
    // Function: updateAction (duyệt/từ chối yêu cầu tham gia)
    // =====================================================================
    @Nested
    @DisplayName("updateAction")
    class UpdateActionByIdTest {

        // UTCID01 - Abnormal: bản ghi thành viên không tồn tại
        @Test
        void updateAction_participantNotFound_throwsNotFound() {
            when(repository.findById(10L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupParticipantService.updateAction(10L, GroupParticipantAction.JOIN));

            assertEquals("Thành viên nhóm không tồn tại", ex.getMessage());
        }

        // UTCID02 - Abnormal: người thực hiện không phải trưởng nhóm
        @Test
        void updateAction_notLeader_throwsAuthorizeException() {
            Group target = group(1L, true);
            GroupParticipant gp = participant(target, user(2L),
                    GroupRole.MEMBER, GroupParticipantAction.PENDING);
            when(repository.findById(10L)).thenReturn(Optional.of(gp));
            when(userService.getCurrentUser()).thenReturn(user(3L));
            when(repository.existsByGroup_GroupIdAndUser_UserIdAndRole(1L, 3L, GroupRole.LEADER))
                    .thenReturn(false);

            GroupAuthorizeException ex = assertThrows(GroupAuthorizeException.class,
                    () -> groupParticipantService.updateAction(10L, GroupParticipantAction.JOIN));

            assertEquals("Bạn không phải là trưởng nhóm của nhóm này", ex.getMessage());
            verify(repository, never()).save(any());
        }

        // UTCID03 - Abnormal: từ chối thành viên đã tham gia (JOIN -> DENIED)
        @Test
        void updateAction_denyJoinedMember_throwsConflict() {
            Group target = group(1L, true);
            GroupParticipant gp = participant(target, user(2L),
                    GroupRole.MEMBER, GroupParticipantAction.JOIN);
            when(repository.findById(10L)).thenReturn(Optional.of(gp));
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(repository.existsByGroup_GroupIdAndUser_UserIdAndRole(anyLong(), anyLong(), any()))
                    .thenReturn(true);

            GroupConflictException ex = assertThrows(GroupConflictException.class,
                    () -> groupParticipantService.updateAction(10L, GroupParticipantAction.DENIED));

            assertEquals("Bạn không thể từ chối thành viên đã tham gia nhóm", ex.getMessage());
            verify(repository, never()).save(any());
        }

        // UTCID04 - Abnormal: chấp nhận lại thành viên đã bị từ chối (DENIED -> JOIN)
        @Test
        void updateAction_acceptDeniedMember_throwsConflict() {
            Group target = group(1L, true);
            GroupParticipant gp = participant(target, user(2L),
                    GroupRole.MEMBER, GroupParticipantAction.DENIED);
            when(repository.findById(10L)).thenReturn(Optional.of(gp));
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(repository.existsByGroup_GroupIdAndUser_UserIdAndRole(anyLong(), anyLong(), any()))
                    .thenReturn(true);

            GroupConflictException ex = assertThrows(GroupConflictException.class,
                    () -> groupParticipantService.updateAction(10L, GroupParticipantAction.JOIN));

            assertEquals("Bạn không thể chấp nhận thành viên đã bị từ chối. "
                    + "Hãy cho thành viên request lại.", ex.getMessage());
        }

        // UTCID05 - Abnormal: kick thành viên đang chờ duyệt (PENDING -> KICKED)
        @Test
        void updateAction_kickPendingMember_throwsConflict() {
            Group target = group(1L, true);
            GroupParticipant gp = participant(target, user(2L),
                    GroupRole.MEMBER, GroupParticipantAction.PENDING);
            when(repository.findById(10L)).thenReturn(Optional.of(gp));
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(repository.existsByGroup_GroupIdAndUser_UserIdAndRole(anyLong(), anyLong(), any()))
                    .thenReturn(true);

            GroupConflictException ex = assertThrows(GroupConflictException.class,
                    () -> groupParticipantService.updateAction(10L, GroupParticipantAction.KICKED));

            assertEquals("Bạn không thể kick thành viên đang chờ duyệt. "
                    + "Hãy từ chối yêu cầu của thành viên này.", ex.getMessage());
        }

        // UTCID06 - Normal: duyệt yêu cầu tham gia (PENDING -> JOIN), cập nhật lại sĩ số
        @Test
        void updateAction_approvePendingMember_setsJoinAndRecounts() {
            Group target = group(1L, true);
            GroupParticipant gp = participant(target, user(2L),
                    GroupRole.MEMBER, GroupParticipantAction.PENDING);
            when(repository.findById(10L)).thenReturn(Optional.of(gp));
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(repository.existsByGroup_GroupIdAndUser_UserIdAndRole(anyLong(), anyLong(), any()))
                    .thenReturn(true);
            when(repository.save(any(GroupParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
            when(repository.findAllByGroup_GroupIdAndAction(1L, GroupParticipantAction.JOIN))
                    .thenReturn(List.of(new GroupParticipant(), new GroupParticipant(),
                            new GroupParticipant(), new GroupParticipant()));

            groupParticipantService.updateAction(10L, GroupParticipantAction.JOIN);

            assertEquals(GroupParticipantAction.JOIN, gp.getAction());
            assertEquals(4, target.getTotalMembers());
            verify(groupRepository).save(target);
        }

        // UTCID07 - Normal: từ chối yêu cầu đang chờ (PENDING -> DENIED)
        @Test
        void updateAction_denyPendingMember_setsDenied() {
            Group target = group(1L, true);
            GroupParticipant gp = participant(target, user(2L),
                    GroupRole.MEMBER, GroupParticipantAction.PENDING);
            when(repository.findById(10L)).thenReturn(Optional.of(gp));
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(repository.existsByGroup_GroupIdAndUser_UserIdAndRole(anyLong(), anyLong(), any()))
                    .thenReturn(true);
            when(repository.save(any(GroupParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
            when(repository.findAllByGroup_GroupIdAndAction(anyLong(), any())).thenReturn(List.of());

            groupParticipantService.updateAction(10L, GroupParticipantAction.DENIED);

            assertEquals(GroupParticipantAction.DENIED, gp.getAction());
        }
    }

    // =====================================================================
    // Function: addUserToGroup
    // =====================================================================
    @Nested
    @DisplayName("addUserToGroup")
    class AddUserToGroupTest {

        // UTCID01 - Abnormal: đã là thành viên của nhóm
        @Test
        void addUserToGroup_alreadyJoined_throwsAlreadyMember() {
            Group target = group(1L, false);
            User member = user(2L);
            when(repository.existsByGroup_GroupIdAndUser_UserId_AndAction(
                    1L, 2L, GroupParticipantAction.JOIN)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupParticipantService.addUserToGroup(member, target, JoinGroupType.LINK));

            assertEquals("Người dùng đã là thành viên của nhóm", ex.getMessage());
            verify(repository, never()).save(any());
        }

        // UTCID02 - Abnormal: đã gửi yêu cầu, đang chờ duyệt
        @Test
        void addUserToGroup_alreadyPending_throwsWaitForApproval() {
            Group target = group(1L, true);
            User member = user(2L);
            when(repository.existsByGroup_GroupIdAndUser_UserId_AndAction(
                    1L, 2L, GroupParticipantAction.JOIN)).thenReturn(false);
            when(repository.existsByGroup_GroupIdAndUser_UserId_AndAction(
                    1L, 2L, GroupParticipantAction.PENDING)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupParticipantService.addUserToGroup(member, target, JoinGroupType.LINK));

            assertEquals("Người dùng đã gửi yêu cầu tham gia nhóm. Hãy đợi trưởng nhóm duyệt",
                    ex.getMessage());
        }

        // UTCID03 - Normal: nhóm cần duyệt + vào bằng LINK -> trạng thái PENDING
        @Test
        void addUserToGroup_requiredApprovalViaLink_setsPending() {
            Group target = group(1L, true);
            User member = user(2L);
            when(repository.existsByGroup_GroupIdAndUser_UserId_AndAction(anyLong(), anyLong(), any()))
                    .thenReturn(false);
            when(repository.existsByGroup_GroupIdAndUser_UserId(1L, 2L)).thenReturn(false);
            when(repository.save(any(GroupParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
            when(repository.findAllByGroup_GroupIdAndAction(anyLong(), any())).thenReturn(List.of());

            GroupParticipant result =
                    groupParticipantService.addUserToGroup(member, target, JoinGroupType.LINK);

            assertEquals(GroupParticipantAction.PENDING, result.getAction());
            assertEquals(GroupRole.MEMBER, result.getRole());
        }

        // UTCID04 - Normal: nhóm cần duyệt nhưng do trưởng nhóm ADD -> vào thẳng JOIN
        @Test
        void addUserToGroup_requiredApprovalButAddedByLeader_setsJoin() {
            Group target = group(1L, true);
            User member = user(2L);
            when(repository.existsByGroup_GroupIdAndUser_UserId_AndAction(anyLong(), anyLong(), any()))
                    .thenReturn(false);
            when(repository.existsByGroup_GroupIdAndUser_UserId(1L, 2L)).thenReturn(false);
            when(repository.save(any(GroupParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
            when(repository.findAllByGroup_GroupIdAndAction(anyLong(), any())).thenReturn(List.of());

            GroupParticipant result =
                    groupParticipantService.addUserToGroup(member, target, JoinGroupType.ADD);

            assertEquals(GroupParticipantAction.JOIN, result.getAction());
        }

        // UTCID05 - Normal: nhóm không cần duyệt + vào bằng LINK -> JOIN ngay
        @Test
        void addUserToGroup_noApprovalNeeded_setsJoin() {
            Group target = group(1L, false);
            User member = user(2L);
            when(repository.existsByGroup_GroupIdAndUser_UserId_AndAction(anyLong(), anyLong(), any()))
                    .thenReturn(false);
            when(repository.existsByGroup_GroupIdAndUser_UserId(1L, 2L)).thenReturn(false);
            when(repository.save(any(GroupParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
            when(repository.findAllByGroup_GroupIdAndAction(anyLong(), any())).thenReturn(List.of());

            GroupParticipant result =
                    groupParticipantService.addUserToGroup(member, target, JoinGroupType.LINK);

            assertEquals(GroupParticipantAction.JOIN, result.getAction());
            assertEquals(GroupStatus.ACTIVE, result.getStatus());
        }

        // UTCID06 - Boundary: từng rời nhóm (LEAVE) -> tái sử dụng bản ghi cũ, quay lại JOIN
        @Test
        void addUserToGroup_previouslyLeft_reusesRecordAndRejoins() {
            Group target = group(1L, false);
            User member = user(2L);
            GroupParticipant old = participant(target, member,
                    GroupRole.MEMBER, GroupParticipantAction.LEAVE);
            when(repository.existsByGroup_GroupIdAndUser_UserId_AndAction(anyLong(), anyLong(), any()))
                    .thenReturn(false);
            when(repository.existsByGroup_GroupIdAndUser_UserId(1L, 2L)).thenReturn(true);
            when(repository.findByGroup_GroupIdAndUser_UserId(1L, 2L)).thenReturn(Optional.of(old));
            when(repository.save(any(GroupParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
            when(repository.findAllByGroup_GroupIdAndAction(anyLong(), any())).thenReturn(List.of());

            GroupParticipant result =
                    groupParticipantService.addUserToGroup(member, target, JoinGroupType.LINK);

            assertSame(old, result);
            assertEquals(GroupParticipantAction.JOIN, result.getAction());
            assertEquals(GroupStatus.ACTIVE, result.getStatus());
        }
    }

    // =====================================================================
    // Function: updateAction(User, Group, action)
    // =====================================================================
    @Nested
    @DisplayName("updateAction(User, Group, action)")
    class UpdateActionByUserTest {

        // UTCID01 - Abnormal: người dùng chưa từng là thành viên nhóm
        @Test
        void updateActionByUser_notAParticipant_throwsNotMember() {
            Group target = group(1L, false);
            User outsider = user(9L);
            when(repository.existsByGroup_GroupIdAndUser_UserId(1L, 9L)).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupParticipantService.updateAction(
                            outsider, target, GroupParticipantAction.LEAVE));

            assertEquals("Người dùng chưa là thành viên của nhóm", ex.getMessage());
            verify(repository, never()).save(any());
        }

        // UTCID02 - Normal: đánh dấu rời nhóm
        @Test
        void updateActionByUser_setsLeaveAction() {
            Group target = group(1L, false);
            User member = user(2L);
            GroupParticipant gp = participant(target, member,
                    GroupRole.MEMBER, GroupParticipantAction.JOIN);
            when(repository.existsByGroup_GroupIdAndUser_UserId(1L, 2L)).thenReturn(true);
            when(repository.findByGroup_GroupIdAndUser_UserId(1L, 2L)).thenReturn(Optional.of(gp));
            when(repository.save(any(GroupParticipant.class))).thenAnswer(inv -> inv.getArgument(0));

            groupParticipantService.updateAction(member, target, GroupParticipantAction.LEAVE);

            assertEquals(GroupParticipantAction.LEAVE, gp.getAction());
        }

        // UTCID03 - Normal: đánh dấu bị kick
        @Test
        void updateActionByUser_setsKickedAction() {
            Group target = group(1L, false);
            User member = user(2L);
            GroupParticipant gp = participant(target, member,
                    GroupRole.MEMBER, GroupParticipantAction.JOIN);
            when(repository.existsByGroup_GroupIdAndUser_UserId(1L, 2L)).thenReturn(true);
            when(repository.findByGroup_GroupIdAndUser_UserId(1L, 2L)).thenReturn(Optional.of(gp));
            when(repository.save(any(GroupParticipant.class))).thenAnswer(inv -> inv.getArgument(0));

            groupParticipantService.updateAction(member, target, GroupParticipantAction.KICKED);

            assertEquals(GroupParticipantAction.KICKED, gp.getAction());
        }
    }

    // =====================================================================
    // Function: isLeader / getGroupParticipant
    // =====================================================================
    @Nested
    @DisplayName("isLeader")
    class IsLeaderTest {

        // UTCID01 - Normal: đúng là trưởng nhóm
        @Test
        void isLeader_leaderRole_returnsTrue() {
            Group target = group(1L, false);
            User leader = user(1L);
            when(repository.findByGroup_GroupIdAndUser_UserId(1L, 1L))
                    .thenReturn(Optional.of(participant(target, leader,
                            GroupRole.LEADER, GroupParticipantAction.JOIN)));

            assertTrue(groupParticipantService.isLeader(leader, target));
        }

        // UTCID02 - Normal: chỉ là thành viên thường
        @Test
        void isLeader_memberRole_returnsFalse() {
            Group target = group(1L, false);
            User member = user(2L);
            when(repository.findByGroup_GroupIdAndUser_UserId(1L, 2L))
                    .thenReturn(Optional.of(participant(target, member,
                            GroupRole.MEMBER, GroupParticipantAction.JOIN)));

            assertFalse(groupParticipantService.isLeader(member, target));
        }

        // UTCID03 - Abnormal: không có bản ghi tham gia -> ném lỗi
        @Test
        void isLeader_noParticipantRecord_throwsNotMember() {
            Group target = group(1L, false);
            User outsider = user(9L);
            when(repository.findByGroup_GroupIdAndUser_UserId(1L, 9L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupParticipantService.isLeader(outsider, target));

            assertEquals("Người dùng không phải là thành viên của nhóm", ex.getMessage());
        }
    }
}
