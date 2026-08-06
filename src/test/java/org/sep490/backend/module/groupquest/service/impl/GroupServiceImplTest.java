package org.sep490.backend.module.groupquest.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.sep490.backend.common.utils.SecurityUtils;
import org.sep490.backend.common.utils.ShareTokenUtils;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.groupquest.entity.Group;
import org.sep490.backend.module.groupquest.entity.GroupParticipant;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupParticipantAction;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupRole;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupStatus;
import org.sep490.backend.module.groupquest.entity.enumuration.JoinGroupType;
import org.sep490.backend.module.groupquest.mapper.GroupMapper;
import org.sep490.backend.module.groupquest.mapper.GroupParticipantMapper;
import org.sep490.backend.module.groupquest.repository.GroupParticipantRepository;
import org.sep490.backend.module.groupquest.repository.GroupRepository;
import org.sep490.backend.module.groupquest.service.inter.GroupParticipantService;
import org.sep490.backend.module.notification.service.FcmService;
import org.sep490.backend.module.user.repository.UserFollowRepository;
import org.sep490.backend.module.user.service.UserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho nghiệp vụ NHÓM (Group Quest).
 * SecurityUtils là static nên phải mockStatic ở @BeforeEach.
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupServiceImplTest {

    @Mock private GroupRepository groupRepository;
    @Mock private UserFollowRepository userFollowRepository;
    @Mock private UserService userService;
    @Mock private GroupParticipantService groupParticipantService;
    @Mock private GroupMapper groupMapper;
    @Mock private GroupParticipantMapper groupParticipantMapper;
    @Mock private GroupParticipantRepository groupParticipantRepository;
    @Mock private FcmService fcmService;

    @InjectMocks private GroupServiceImpl groupService;

    private MockedStatic<SecurityUtils> securityUtils;

    /** Mặc định coi như đã đăng nhập; test nào cần "chưa đăng nhập" sẽ ghi đè. */
    @BeforeEach
    void setUp() {
        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserKeyCloakId)
                .thenReturn(Optional.of("kc-001"));
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    private static User user(Long userId, String username) {
        User user = new User();
        user.setUserId(userId);
        user.setUsername(username);
        return user;
    }

    private static Group group(Long groupId, User leader, GroupStatus status) {
        return Group.builder()
                .groupId(groupId)
                .createdBy(leader)
                .groupName("Nhóm phượt Đà Lạt")
                .totalMembers(3)
                .status(status)
                .shareToken("ABCD123456")
                .expireAt(LocalDateTime.now().plusDays(1))
                .requiredApproval(false)
                .build();
    }

    /** Trưởng nhóm dùng cho getLeaderFromGroup(). */
    private void leaderOfGroup(Long groupId, User leader) {
        GroupParticipant gp = new GroupParticipant();
        gp.setUser(leader);
        gp.setRole(GroupRole.LEADER);
        when(groupParticipantRepository.findByGroup_GroupIdAndRole(groupId, GroupRole.LEADER))
                .thenReturn(gp);
    }

    // =====================================================================
    // Function: leaveGroup
    // =====================================================================
    @Nested
    @DisplayName("leaveGroup")
    class LeaveGroupTest {

        // UTCID01 - Abnormal: chưa đăng nhập
        @Test
        void leaveGroup_notLoggedIn_throwsNotLoggedIn() {
            securityUtils.when(SecurityUtils::getCurrentUserKeyCloakId).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.leaveGroup(1L));

            assertEquals("Người dùng chưa đăng nhập: leaveGroup", ex.getMessage());
        }

        // UTCID02 - Abnormal: nhóm không tồn tại
        @Test
        void leaveGroup_groupNotFound_throwsGroupNotFound() {
            when(groupRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.leaveGroup(1L));

            assertEquals("Nhóm không tồn tại", ex.getMessage());
        }

        // UTCID03 - Abnormal: nhóm đã bị xóa
        @Test
        void leaveGroup_deletedGroup_throwsGroupDeleted() {
            User leader = user(1L, "leader");
            when(groupRepository.findById(1L))
                    .thenReturn(Optional.of(group(1L, leader, GroupStatus.DELETED)));
            when(userService.getCurrentUser()).thenReturn(user(2L, "member"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.leaveGroup(1L));

            assertEquals("Nhóm đã bị xóa", ex.getMessage());
        }

        // UTCID04 - Abnormal: người dùng không phải thành viên nhóm
        @Test
        void leaveGroup_notAParticipant_throwsAuthorizeException() {
            User leader = user(1L, "leader");
            Group target = group(1L, leader, GroupStatus.ACTIVE);
            User outsider = user(9L, "nguoila");
            when(groupRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(outsider);
            when(groupParticipantService.isParticipant(outsider, target)).thenReturn(false);

            GroupAuthorizeException ex = assertThrows(GroupAuthorizeException.class,
                    () -> groupService.leaveGroup(1L));

            assertEquals("Người dùng không phải là thành viên của nhóm", ex.getMessage());
        }

        // UTCID05 - Abnormal: trưởng nhóm không được rời nhóm
        @Test
        void leaveGroup_leaderTriesToLeave_throwsLeaderCannotLeave() {
            User leader = user(1L, "leader");
            Group target = group(1L, leader, GroupStatus.ACTIVE);
            when(groupRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(leader);
            when(groupParticipantService.isParticipant(leader, target)).thenReturn(true);
            when(groupParticipantService.isLeader(leader, target)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.leaveGroup(1L));

            assertEquals("Trưởng nhóm không thể rời nhóm. Hãy chuyển quyền trưởng nhóm cho người khác "
                    + "trước khi rời nhóm", ex.getMessage());
            verify(groupParticipantService, never())
                    .updateAction(any(User.class), any(Group.class), any());
        }

        // UTCID06 - Abnormal: đã rời nhóm này trước đó
        @Test
        void leaveGroup_alreadyLeft_throwsAlreadyLeft() {
            User leader = user(1L, "leader");
            Group target = group(1L, leader, GroupStatus.ACTIVE);
            User member = user(2L, "member");
            when(groupRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(member);
            when(groupParticipantService.isParticipant(member, target)).thenReturn(true);
            when(groupParticipantService.isLeader(member, target)).thenReturn(false);
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserId_AndAction(
                    1L, 2L, GroupParticipantAction.LEAVE)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.leaveGroup(1L));

            assertEquals("Bạn đã rời nhóm này", ex.getMessage());
        }

        // UTCID07 - Abnormal: đang chờ duyệt (PENDING) -> chưa phải thành viên nên không thể rời
        @Test
        void leaveGroup_stillPending_throwsNotYetMember() {
            User leader = user(1L, "leader");
            Group target = group(1L, leader, GroupStatus.ACTIVE);
            User member = user(2L, "member");
            when(groupRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(member);
            when(groupParticipantService.isParticipant(member, target)).thenReturn(true);
            when(groupParticipantService.isLeader(member, target)).thenReturn(false);
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserId_AndAction(
                    1L, 2L, GroupParticipantAction.LEAVE)).thenReturn(false);
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserId_AndAction(
                    1L, 2L, GroupParticipantAction.PENDING)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.leaveGroup(1L));

            assertEquals("Bạn chưa là thành viên để rời nhóm này", ex.getMessage());
        }

        // UTCID08 - Normal: rời nhóm thành công -> action = LEAVE, cập nhật lại sĩ số
        @Test
        void leaveGroup_validMember_setsLeaveActionAndRecountsMembers() {
            User leader = user(1L, "leader");
            Group target = group(1L, leader, GroupStatus.ACTIVE);
            User member = user(2L, "member");
            when(groupRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(member);
            when(groupParticipantService.isParticipant(member, target)).thenReturn(true);
            when(groupParticipantService.isLeader(member, target)).thenReturn(false);
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserId_AndAction(
                    anyLong(), anyLong(), any())).thenReturn(false);
            when(groupParticipantRepository.findAllByGroup_GroupIdAndAction(
                    1L, GroupParticipantAction.JOIN))
                    .thenReturn(List.of(new GroupParticipant(), new GroupParticipant()));
            leaderOfGroup(1L, leader);

            groupService.leaveGroup(1L);

            verify(groupParticipantService)
                    .updateAction(member, target, GroupParticipantAction.LEAVE);
            assertEquals(2, target.getTotalMembers());
            verify(groupRepository).save(target);
        }
    }

    // =====================================================================
    // Function: addUserToGroup
    // =====================================================================
    @Nested
    @DisplayName("addUserToGroup")
    class AddUserToGroupTest {

        // UTCID01 - Abnormal: chưa đăng nhập
        @Test
        void addUserToGroup_notLoggedIn_throwsNotLoggedIn() {
            securityUtils.when(SecurityUtils::getCurrentUserKeyCloakId).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.addUserToGroup(2L, 1L));

            assertEquals("Người dùng chưa đăng nhập: addUserToGroup", ex.getMessage());
        }

        // UTCID02 - Abnormal: nhóm đã bị xóa
        @Test
        void addUserToGroup_deletedGroup_throwsGroupDeleted() {
            User leader = user(1L, "leader");
            when(groupRepository.findById(1L))
                    .thenReturn(Optional.of(group(1L, leader, GroupStatus.DELETED)));
            when(userService.getCurrentUser()).thenReturn(leader);
            when(userService.getUserById(2L)).thenReturn(user(2L, "member"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.addUserToGroup(2L, 1L));

            assertEquals("Không thể add thành viên khi nhóm đã bị xóa", ex.getMessage());
        }

        // UTCID03 - Abnormal: không phải trưởng nhóm
        @Test
        void addUserToGroup_notLeader_throwsAuthorizeException() {
            User leader = user(1L, "leader");
            Group target = group(1L, leader, GroupStatus.ACTIVE);
            User someone = user(3L, "thanhvien");
            when(groupRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(someone);
            when(userService.getUserById(2L)).thenReturn(user(2L, "moi"));

            GroupAuthorizeException ex = assertThrows(GroupAuthorizeException.class,
                    () -> groupService.addUserToGroup(2L, 1L));

            assertEquals("Chỉ có trưởng nhóm mới có thể add thành viên", ex.getMessage());
        }

        // UTCID04 - Abnormal: trưởng nhóm tự add chính mình
        @Test
        void addUserToGroup_addingSelf_throwsCannotAddSelf() {
            User leader = user(1L, "leader");
            Group target = group(1L, leader, GroupStatus.ACTIVE);
            when(groupRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(leader);
            when(userService.getUserById(1L)).thenReturn(leader);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.addUserToGroup(1L, 1L));

            assertEquals("Không thể add chính mình vào nhóm", ex.getMessage());
        }

        // UTCID05 - Abnormal: hai người chưa follow nhau
        @Test
        void addUserToGroup_notMutualFollow_throwsMustFollowEachOther() {
            User leader = user(1L, "leader");
            Group target = group(1L, leader, GroupStatus.ACTIVE);
            User newMember = user(2L, "moi");
            when(groupRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(leader);
            when(userService.getUserById(2L)).thenReturn(newMember);
            when(userFollowRepository.existsByFollowerAndFollowing(newMember, leader)).thenReturn(true);
            when(userFollowRepository.existsByFollowerAndFollowing(leader, newMember)).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.addUserToGroup(2L, 1L));

            assertEquals("Cả 2 phải theo dõi nhau để add vào group", ex.getMessage());
            verify(groupParticipantService, never())
                    .addUserToGroup(any(), any(), any());
        }

        // UTCID06 - Normal: add thành công qua kiểu ADD
        @Test
        void addUserToGroup_mutualFollow_addsWithTypeAdd() {
            User leader = user(1L, "leader");
            Group target = group(1L, leader, GroupStatus.ACTIVE);
            User newMember = user(2L, "moi");
            when(groupRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(leader);
            when(userService.getUserById(2L)).thenReturn(newMember);
            when(userFollowRepository.existsByFollowerAndFollowing(any(), any())).thenReturn(true);
            leaderOfGroup(1L, leader);

            groupService.addUserToGroup(2L, 1L);

            verify(groupParticipantService).addUserToGroup(newMember, target, JoinGroupType.ADD);
        }
    }

    // =====================================================================
    // Function: kickUserFromGroup
    // =====================================================================
    @Nested
    @DisplayName("kickUserFromGroup")
    class KickUserFromGroupTest {

        // UTCID01 - Abnormal: nhóm đã bị xóa
        @Test
        void kickUserFromGroup_deletedGroup_throwsGroupDeleted() {
            User leader = user(1L, "leader");
            when(groupRepository.findById(1L))
                    .thenReturn(Optional.of(group(1L, leader, GroupStatus.DELETED)));
            when(userService.getCurrentUser()).thenReturn(leader);
            when(userService.getUserById(2L)).thenReturn(user(2L, "member"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.kickUserFromGroup(2L, 1L));

            assertEquals("Nhóm đã bị xóa", ex.getMessage());
        }

        // UTCID02 - Abnormal: người thực hiện không phải trưởng nhóm
        @Test
        void kickUserFromGroup_notLeader_throwsAuthorizeException() {
            User leader = user(1L, "leader");
            Group target = group(1L, leader, GroupStatus.ACTIVE);
            User someone = user(3L, "thanhvien");
            when(groupRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(someone);
            when(userService.getUserById(2L)).thenReturn(user(2L, "member"));
            when(groupParticipantService.isLeader(someone, target)).thenReturn(false);

            GroupAuthorizeException ex = assertThrows(GroupAuthorizeException.class,
                    () -> groupService.kickUserFromGroup(2L, 1L));

            assertEquals("Chỉ có trưởng nhóm mới có thể kick thành viên", ex.getMessage());
        }

        // UTCID03 - Abnormal: trưởng nhóm tự kick chính mình
        @Test
        void kickUserFromGroup_kickingSelf_throwsCannotKickSelf() {
            User leader = user(1L, "leader");
            Group target = group(1L, leader, GroupStatus.ACTIVE);
            when(groupRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(leader);
            when(userService.getUserById(1L)).thenReturn(leader);
            when(groupParticipantService.isLeader(leader, target)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.kickUserFromGroup(1L, 1L));

            assertEquals("Bạn không thể kick chính mình", ex.getMessage());
        }

        // UTCID04 - Abnormal: thành viên đã bị kick trước đó
        @Test
        void kickUserFromGroup_alreadyKicked_throwsAlreadyKicked() {
            User leader = user(1L, "leader");
            Group target = group(1L, leader, GroupStatus.ACTIVE);
            User member = user(2L, "member");
            when(groupRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(leader);
            when(userService.getUserById(2L)).thenReturn(member);
            when(groupParticipantService.isLeader(leader, target)).thenReturn(true);
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserId_AndAction(
                    1L, 2L, GroupParticipantAction.KICKED)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.kickUserFromGroup(2L, 1L));

            assertEquals("Thành viên này đã bị kick khỏi nhóm", ex.getMessage());
            verify(groupParticipantService, never())
                    .updateAction(any(User.class), any(Group.class), any());
        }

        // UTCID05 - Normal: kick thành công -> action = KICKED, cập nhật sĩ số
        @Test
        void kickUserFromGroup_validLeader_setsKickedActionAndRecounts() {
            User leader = user(1L, "leader");
            Group target = group(1L, leader, GroupStatus.ACTIVE);
            User member = user(2L, "member");
            when(groupRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(leader);
            when(userService.getUserById(2L)).thenReturn(member);
            when(groupParticipantService.isLeader(leader, target)).thenReturn(true);
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserId_AndAction(
                    anyLong(), anyLong(), any())).thenReturn(false);
            when(groupParticipantRepository.findAllByGroup_GroupIdAndAction(
                    1L, GroupParticipantAction.JOIN))
                    .thenReturn(List.of(new GroupParticipant()));
            leaderOfGroup(1L, leader);

            groupService.kickUserFromGroup(2L, 1L);

            verify(groupParticipantService)
                    .updateAction(member, target, GroupParticipantAction.KICKED);
            assertEquals(1, target.getTotalMembers());
            verify(groupRepository).save(target);
        }
    }

    // =====================================================================
    // Function: joinGroup
    // =====================================================================
    @Nested
    @DisplayName("joinGroup")
    class JoinGroupTest {

        /** Token hợp lệ 10 ký tự, 4 ký tự đầu mã hóa groupId = 1. */
        private static String validTokenFor(long groupId) {
            return ShareTokenUtils.generateToken(groupId);
        }

        // UTCID01 - Abnormal: token sai độ dài -> IllegalArgumentException từ GroupUtils
        @Test
        void joinGroup_invalidTokenLength_throwsIllegalArgument() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> groupService.joinGroup("SAI"));

            assertEquals("Token không hợp lệ. Độ dài bắt buộc là 10 ký tự.", ex.getMessage());
        }

        // UTCID02 - Abnormal: nhóm đã bị xóa
        @Test
        void joinGroup_deletedGroup_throwsGroupDeleted() {
            String token = validTokenFor(1L);
            User leader = user(1L, "leader");
            Group target = group(1L, leader, GroupStatus.DELETED);
            target.setShareToken(token);
            when(userService.getCurrentUser()).thenReturn(user(2L, "khach"));
            when(groupRepository.findById(1L)).thenReturn(Optional.of(target));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.joinGroup(token));

            assertEquals("Nhóm đã bị xóa", ex.getMessage());
        }

        // UTCID03 - Abnormal: token không khớp với token đang lưu của nhóm
        @Test
        void joinGroup_tokenMismatch_throwsInvalidToken() {
            String token = validTokenFor(1L);
            User leader = user(1L, "leader");
            Group target = group(1L, leader, GroupStatus.ACTIVE);
            target.setShareToken("0000000000");
            when(userService.getCurrentUser()).thenReturn(user(2L, "khach"));
            when(groupRepository.findById(1L)).thenReturn(Optional.of(target));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.joinGroup(token));

            assertEquals("Token không hợp lệ", ex.getMessage());
        }

        // UTCID04 - Abnormal: token đã quá 24h
        @Test
        void joinGroup_expiredToken_throwsTokenExpired() {
            String token = validTokenFor(1L);
            User leader = user(1L, "leader");
            Group target = group(1L, leader, GroupStatus.ACTIVE);
            target.setShareToken(token);
            target.setExpireAt(LocalDateTime.now().minusMinutes(1));
            when(userService.getCurrentUser()).thenReturn(user(2L, "khach"));
            when(groupRepository.findById(1L)).thenReturn(Optional.of(target));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.joinGroup(token));

            assertEquals("Token đã hết hạn", ex.getMessage());
            verify(groupParticipantService, never()).addUserToGroup(any(), any(), any());
        }

        // UTCID05 - Normal: vào nhóm bằng link thành công -> kiểu tham gia LINK
        @Test
        void joinGroup_validToken_addsUserWithTypeLink() {
            String token = validTokenFor(1L);
            User leader = user(1L, "leader");
            Group target = group(1L, leader, GroupStatus.ACTIVE);
            target.setShareToken(token);
            User guest = user(2L, "khach");
            when(userService.getCurrentUser()).thenReturn(guest);
            when(groupRepository.findById(1L)).thenReturn(Optional.of(target));
            leaderOfGroup(1L, leader);

            groupService.joinGroup(token);

            verify(groupParticipantService).addUserToGroup(guest, target, JoinGroupType.LINK);
        }
    }

    // =====================================================================
    // Function: refreshSharedToken
    // =====================================================================
    @Nested
    @DisplayName("refreshSharedToken")
    class RefreshSharedTokenTest {

        // UTCID01 - Abnormal: nhóm đã bị xóa
        @Test
        void refreshSharedToken_deletedGroup_throwsGroupDeleted() {
            User leader = user(1L, "leader");
            when(userService.getCurrentUser()).thenReturn(leader);
            when(groupRepository.findById(1L))
                    .thenReturn(Optional.of(group(1L, leader, GroupStatus.DELETED)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.refreshSharedToken(1L));

            assertEquals("Nhóm đã bị xóa", ex.getMessage());
        }

        // UTCID02 - Abnormal: người dùng không có bản ghi tham gia nhóm
        @Test
        void refreshSharedToken_notAParticipant_throwsAuthorizeException() {
            User leader = user(1L, "leader");
            User outsider = user(9L, "nguoila");
            when(userService.getCurrentUser()).thenReturn(outsider);
            when(groupRepository.findById(1L))
                    .thenReturn(Optional.of(group(1L, leader, GroupStatus.ACTIVE)));
            when(groupParticipantRepository.findByGroup_GroupIdAndUser_UserId(1L, 9L))
                    .thenReturn(Optional.empty());

            GroupAuthorizeException ex = assertThrows(GroupAuthorizeException.class,
                    () -> groupService.refreshSharedToken(1L));

            assertEquals("Chỉ có trưởng nhóm mới có thể tạo mới invite code", ex.getMessage());
        }

        // UTCID03 - Abnormal: là thành viên nhưng không phải trưởng nhóm
        @Test
        void refreshSharedToken_memberNotLeader_throwsAuthorizeException() {
            User leader = user(1L, "leader");
            User member = user(2L, "member");
            GroupParticipant gp = new GroupParticipant();
            gp.setUser(member);
            gp.setRole(GroupRole.MEMBER);
            when(userService.getCurrentUser()).thenReturn(member);
            when(groupRepository.findById(1L))
                    .thenReturn(Optional.of(group(1L, leader, GroupStatus.ACTIVE)));
            when(groupParticipantRepository.findByGroup_GroupIdAndUser_UserId(1L, 2L))
                    .thenReturn(Optional.of(gp));

            GroupAuthorizeException ex = assertThrows(GroupAuthorizeException.class,
                    () -> groupService.refreshSharedToken(1L));

            assertEquals("Chỉ có trưởng nhóm mới có thể tạo mới invite code", ex.getMessage());
            verify(groupRepository, never()).save(any());
        }

        // UTCID04 - Normal: trưởng nhóm tạo lại token -> token đổi, hạn mới +24h
        @Test
        void refreshSharedToken_leader_generatesNewTokenAndExpiry() {
            User leader = user(1L, "leader");
            Group target = group(1L, leader, GroupStatus.ACTIVE);
            target.setShareToken("0000000000");
            GroupParticipant gp = new GroupParticipant();
            gp.setUser(leader);
            gp.setRole(GroupRole.LEADER);
            when(userService.getCurrentUser()).thenReturn(leader);
            when(groupRepository.findById(1L)).thenReturn(Optional.of(target));
            when(groupParticipantRepository.findByGroup_GroupIdAndUser_UserId(1L, 1L))
                    .thenReturn(Optional.of(gp));
            leaderOfGroup(1L, leader);

            groupService.refreshSharedToken(1L);

            assertNotEquals("0000000000", target.getShareToken());
            assertEquals(10, target.getShareToken().length());
            assertTrue(target.getExpireAt().isAfter(LocalDateTime.now().plusHours(23)));
            verify(groupRepository).save(target);
        }
    }

    // =====================================================================
    // Function: createGroup
    // =====================================================================
    @Nested
    @DisplayName("createGroup")
    class CreateGroupTest {

        private static org.sep490.backend.module.groupquest.dto.request.GroupRequest groupRequest() {
            org.sep490.backend.module.groupquest.dto.request.GroupRequest request =
                    new org.sep490.backend.module.groupquest.dto.request.GroupRequest();
            request.setGroupName("  Nhóm phượt Đà Lạt  ");
            request.setUserIds(List.of(2L, 3L));
            return request;
        }

        // UTCID01 - Abnormal: chưa đăng nhập
        @Test
        void createGroup_notLoggedIn_throwsNotLoggedIn() {
            securityUtils.when(SecurityUtils::getCurrentUserKeyCloakId).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.createGroup(groupRequest()));

            assertEquals("Người dùng chưa đăng nhập: createGroup", ex.getMessage());
        }

        // UTCID02 - Abnormal: không có thành viên nào follow nhau
        @Test
        void createGroup_noMutualFollowers_throwsNeedMembers() {
            User leader = user(1L, "leader");
            when(userService.getCurrentUser()).thenReturn(leader);
            when(userFollowRepository.findMutualFollowerIds(eq(1L), anyList())).thenReturn(List.of());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.createGroup(groupRequest()));

            assertEquals("Bạn cần ít nhất 2 thành viên follow nhau để tạo nhóm", ex.getMessage());
            verify(groupRepository, never()).save(any());
        }

        // UTCID03 - Abnormal: repository trả null -> vẫn báo lỗi thiếu thành viên
        @Test
        void createGroup_nullMutualFollowers_throwsNeedMembers() {
            User leader = user(1L, "leader");
            when(userService.getCurrentUser()).thenReturn(leader);
            when(userFollowRepository.findMutualFollowerIds(eq(1L), anyList())).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.createGroup(groupRequest()));

            assertEquals("Bạn cần ít nhất 2 thành viên follow nhau để tạo nhóm", ex.getMessage());
        }

        // UTCID04 - Normal: tạo nhóm thành công -> status ACTIVE, trim tên, sinh shareToken
        @Test
        void createGroup_validRequest_createsActiveGroupWithShareToken() {
            User leader = user(1L, "leader");
            when(userService.getCurrentUser()).thenReturn(leader);
            when(userFollowRepository.findMutualFollowerIds(eq(1L), anyList()))
                    .thenReturn(List.of(2L, 3L));
            when(userService.getUsersByIds(List.of(2L, 3L)))
                    .thenReturn(List.of(user(2L, "a"), user(3L, "b")));
            when(groupRepository.save(any(Group.class))).thenAnswer(inv -> {
                Group saved = inv.getArgument(0);
                if (saved.getGroupId() == null) {
                    saved.setGroupId(1L);
                }
                return saved;
            });
            leaderOfGroup(1L, leader);

            groupService.createGroup(groupRequest());

            org.mockito.ArgumentCaptor<Group> captor =
                    org.mockito.ArgumentCaptor.forClass(Group.class);
            verify(groupRepository, times(2)).save(captor.capture());
            Group saved = captor.getValue();
            assertEquals("Nhóm phượt Đà Lạt", saved.getGroupName());
            assertEquals(GroupStatus.ACTIVE, saved.getStatus());
            assertEquals(3, saved.getTotalMembers());
            assertEquals(10, saved.getShareToken().length());
            verify(groupParticipantService).addLeaderToGroup(eq(leader), any(Group.class));
        }

        // UTCID05 - Boundary: 1 người hợp lệ, 1 người không follow -> vẫn tạo nhóm + gửi noti cảnh báo
        @Test
        void createGroup_someInvalidMembers_createsGroupAndPushesNotification() {
            User leader = user(1L, "leader");
            when(userService.getCurrentUser()).thenReturn(leader);
            when(userService.getUserById(1L)).thenReturn(leader);
            when(userFollowRepository.findMutualFollowerIds(eq(1L), anyList())).thenReturn(List.of(2L));
            when(userService.getUsersByIds(List.of(2L))).thenReturn(List.of(user(2L, "a")));
            when(userService.getUsersByIds(List.of(3L))).thenReturn(List.of(user(3L, "b")));
            when(groupRepository.save(any(Group.class))).thenAnswer(inv -> {
                Group saved = inv.getArgument(0);
                if (saved.getGroupId() == null) {
                    saved.setGroupId(1L);
                }
                return saved;
            });
            leaderOfGroup(1L, leader);

            groupService.createGroup(groupRequest());

            verify(fcmService).sendPushNotification(any(), anyString(), anyString(), any(), eq(1L));
            verify(groupRepository, times(2)).save(any(Group.class));
        }
    }

    // =====================================================================
    // Function: updateGroup
    // =====================================================================
    @Nested
    @DisplayName("updateGroup")
    class UpdateGroupTest {

        private static org.sep490.backend.module.groupquest.dto.request.GroupUpdateRequest updateRequest() {
            org.sep490.backend.module.groupquest.dto.request.GroupUpdateRequest request =
                    new org.sep490.backend.module.groupquest.dto.request.GroupUpdateRequest();
            request.setGroupName("Nhóm phượt Nha Trang");
            return request;
        }

        // UTCID01 - Abnormal: không phải trưởng nhóm
        @Test
        void updateGroup_notLeader_throwsAuthorizeException() {
            when(userService.getCurrentUser()).thenReturn(user(2L, "member"));
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserIdAndRole(
                    1L, 2L, GroupRole.LEADER)).thenReturn(false);

            GroupAuthorizeException ex = assertThrows(GroupAuthorizeException.class,
                    () -> groupService.updateGroup(1L, updateRequest()));

            assertEquals("Chỉ có trưởng nhóm mới có thể update thông tin nhóm", ex.getMessage());
            verify(groupRepository, never()).save(any());
        }

        // UTCID02 - Abnormal: nhóm đã bị xóa
        @Test
        void updateGroup_deletedGroup_throwsGroupDeleted() {
            User leader = user(1L, "leader");
            when(userService.getCurrentUser()).thenReturn(leader);
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserIdAndRole(
                    anyLong(), anyLong(), any())).thenReturn(true);
            when(groupRepository.findById(1L))
                    .thenReturn(Optional.of(group(1L, leader, GroupStatus.DELETED)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.updateGroup(1L, updateRequest()));

            assertEquals("Nhóm đã bị xóa", ex.getMessage());
        }

        // UTCID03 - Normal: đổi tên nhóm thành công
        @Test
        void updateGroup_leaderChangesName_savesNewName() {
            User leader = user(1L, "leader");
            Group target = group(1L, leader, GroupStatus.ACTIVE);
            when(userService.getCurrentUser()).thenReturn(leader);
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserIdAndRole(
                    anyLong(), anyLong(), any())).thenReturn(true);
            when(groupRepository.findById(1L)).thenReturn(Optional.of(target));
            leaderOfGroup(1L, leader);

            groupService.updateGroup(1L, updateRequest());

            assertEquals("Nhóm phượt Nha Trang", target.getGroupName());
            verify(groupRepository).save(target);
        }

        // UTCID04 - Boundary: requiredApproval = null -> giữ nguyên giá trị cũ
        @Test
        void updateGroup_nullRequiredApproval_keepsOldValue() {
            User leader = user(1L, "leader");
            Group target = group(1L, leader, GroupStatus.ACTIVE);
            target.setRequiredApproval(true);
            when(userService.getCurrentUser()).thenReturn(leader);
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserIdAndRole(
                    anyLong(), anyLong(), any())).thenReturn(true);
            when(groupRepository.findById(1L)).thenReturn(Optional.of(target));
            leaderOfGroup(1L, leader);

            org.sep490.backend.module.groupquest.dto.request.GroupUpdateRequest request = updateRequest();
            request.setRequiredApproval(null);

            groupService.updateGroup(1L, request);

            assertTrue(target.getRequiredApproval());
        }
    }

    // =====================================================================
    // Function: deleteGroup
    // =====================================================================
    @Nested
    @DisplayName("deleteGroup")
    class DeleteGroupTest {

        // UTCID01 - Abnormal: không phải trưởng nhóm
        @Test
        void deleteGroup_notLeader_throwsAuthorizeException() {
            when(userService.getCurrentUser()).thenReturn(user(2L, "member"));
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserIdAndRole(
                    1L, 2L, GroupRole.LEADER)).thenReturn(false);

            GroupAuthorizeException ex = assertThrows(GroupAuthorizeException.class,
                    () -> groupService.deleteGroup(1L));

            assertEquals("Chỉ có trưởng nhóm mới có thể xóa nhóm", ex.getMessage());
        }

        // UTCID02 - Abnormal: nhóm đã bị xóa trước đó
        @Test
        void deleteGroup_alreadyDeleted_throwsGroupDeleted() {
            User leader = user(1L, "leader");
            when(userService.getCurrentUser()).thenReturn(leader);
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserIdAndRole(
                    anyLong(), anyLong(), any())).thenReturn(true);
            when(groupRepository.findById(1L))
                    .thenReturn(Optional.of(group(1L, leader, GroupStatus.DELETED)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> groupService.deleteGroup(1L));

            assertEquals("Nhóm đã bị xóa", ex.getMessage());
        }

        // UTCID03 - Normal: xóa nhóm -> status DELETED, mọi thành viên JOIN/PENDING chuyển DISMISSED
        @Test
        void deleteGroup_leader_dismissesActiveParticipants() {
            User leader = user(1L, "leader");
            Group target = group(1L, leader, GroupStatus.ACTIVE);

            GroupParticipant joined = new GroupParticipant();
            joined.setAction(GroupParticipantAction.JOIN);
            GroupParticipant pending = new GroupParticipant();
            pending.setAction(GroupParticipantAction.PENDING);
            GroupParticipant kicked = new GroupParticipant();
            kicked.setAction(GroupParticipantAction.KICKED);

            when(userService.getCurrentUser()).thenReturn(leader);
            when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserIdAndRole(
                    anyLong(), anyLong(), any())).thenReturn(true);
            when(groupRepository.findById(1L)).thenReturn(Optional.of(target));
            when(groupParticipantService.getGroupParticipants(1L))
                    .thenReturn(List.of(joined, pending, kicked));
            leaderOfGroup(1L, leader);

            groupService.deleteGroup(1L);

            assertEquals(GroupStatus.DELETED, target.getStatus());
            assertEquals(GroupParticipantAction.DISMISSED, joined.getAction());
            assertEquals(GroupParticipantAction.DISMISSED, pending.getAction());
            // Thành viên đã bị kick trước đó thì giữ nguyên trạng thái
            assertEquals(GroupParticipantAction.KICKED, kicked.getAction());
        }
    }
}
