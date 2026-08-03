package org.sep490.backend.module.groupquest.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.exception.GroupAuthorizeException;
import org.sep490.backend.common.utils.GroupUtils;
import org.sep490.backend.common.utils.SecurityUtils;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.groupquest.dto.request.GroupRequest;
import org.sep490.backend.module.groupquest.dto.request.GroupUpdateRequest;
import org.sep490.backend.module.groupquest.dto.response.GroupParticipantResponse;
import org.sep490.backend.module.groupquest.dto.response.GroupResponse;
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
import org.sep490.backend.module.groupquest.service.inter.GroupService;
import org.sep490.backend.module.notification.entity.enumeration.NotificationType;
import org.sep490.backend.module.notification.service.FcmService;
import org.sep490.backend.module.user.repository.UserFollowRepository;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GroupServiceImpl implements GroupService {

    GroupRepository groupRepository;
    UserFollowRepository userFollowRepository;
    UserService userService;
    GroupParticipantService groupParticipantService;
    GroupMapper groupMapper;
    GroupParticipantMapper groupParticipantMapper;
    GroupParticipantRepository groupParticipantRepository;
    FcmService fcmService;

    @Override
    @Transactional
    public GroupResponse createGroup(GroupRequest request) {

        User user = userService.getCurrentUser();
        List<User> members = validateListMember(user.getUserId(), request.getUserIds(), request.getGroupName());

        Group group = Group.builder()
                .createdBy(user)
                .groupName(request.getGroupName().trim())
                .totalMembers(members.size() + 1)
                .shareToken(null)
                .expireAt(null)
                .status(GroupStatus.ACTIVE)
                .build();

        group = groupRepository.save(group);

        String shareToken = GroupUtils.generateToken(group.getGroupId());
        LocalDateTime expireTime = LocalDateTime.now().plusDays(1); // expired after 24 hours

        group.setExpireAt(expireTime);
        group.setShareToken(shareToken);
        groupRepository.save(group);

        groupParticipantService.addLeaderToGroup(user, group);
        groupParticipantService.addUsersToGroup(members, group);

        Long leaderId = getLeaderFromGroup(group.getGroupId());
        return groupMapper.toResponse(group, leaderId);
    }


    @Override
    @Transactional
    public GroupResponse updateGroup(Long groupId, GroupUpdateRequest request) {

        User user = userService.getCurrentUser();
        Group group = getGroup(groupId);

        if(group.getStatus().equals(GroupStatus.DELETED)) {
            throw new BusinessException("Nhóm đã bị xóa");
        }

        if(!groupParticipantRepository.existsByGroup_GroupIdAndUser_UserIdAndRole(groupId, user.getUserId(), GroupRole.LEADER)) {
            throw new GroupAuthorizeException("Chỉ có trưởng nhóm mới có thể update thông tin nhóm");
        }

        group.setGroupName(request.getGroupName());

        if(request.getRequiredApproval() != null) {
            group.setRequiredApproval(request.getRequiredApproval());
        }

        groupRepository.save(group);

        Long leaderId = getLeaderFromGroup(groupId);
        return groupMapper.toResponse(group, leaderId);
    }


    @Override
    @Transactional
    public GroupResponse deleteGroup(Long groupId) {

        User user = userService.getCurrentUser();

        if(!groupParticipantRepository.existsByGroup_GroupIdAndUser_UserIdAndRole(groupId, user.getUserId(), GroupRole.LEADER)) {
            throw new GroupAuthorizeException("Chỉ có trưởng nhóm mới có thể xóa nhóm");
        }

        Group group = getGroup(groupId);

        if(group.getStatus().equals(GroupStatus.DELETED)) {
            throw new BusinessException("Nhóm đã bị xóa");
        }

        group.setStatus(GroupStatus.DELETED);
        groupRepository.save(group);

        // implement update GP.action to dismiss
        List<GroupParticipant> gps = groupParticipantService.getGroupParticipants(groupId);

        for(GroupParticipant gp : gps) {
            if(gp.getAction().equals(GroupParticipantAction.JOIN) || gp.getAction().equals(GroupParticipantAction.PENDING)) {
                gp.setAction(GroupParticipantAction.DISMISSED);
                gp.setStatus(GroupStatus.DELETED);
            }
        }

        groupParticipantRepository.saveAll(gps);


        Long leaderId = getLeaderFromGroup(groupId);
        return groupMapper.toResponse(group, leaderId);
    }


    @Override
    @Transactional(readOnly = true)
    public List<GroupResponse> getMyGroups() {

        isLoggedIn("getMyGroups");

        List<GroupParticipant> gps = groupParticipantService.getMyParticipants();

        List<Group> groups = gps.stream()
                .map(GroupParticipant::getGroup)
                .toList();

        return groups.stream()
                .map(group -> groupMapper.toResponse(group, getLeaderFromGroup(group.getGroupId())))
                .collect(Collectors.toList());
    }


    @Override
    @Transactional(readOnly = true)
    public GroupResponse getDetails(Long groupId) {

        Group group = getGroup(groupId);

        if(group.getStatus().equals(GroupStatus.DELETED)) {
            throw new BusinessException("Nhóm đã bị xóa");
        }

        User user = userService.getCurrentUser();

        if(!groupParticipantRepository.existsByGroup_GroupIdAndUser_UserId_AndAction(groupId, user.getUserId(), GroupParticipantAction.JOIN)) {
            throw new GroupAuthorizeException("Bạn không phải là thành viên của nhóm");
        }

        return groupMapper.toResponse(group, getLeaderFromGroup(groupId));
    }


    @Override
    @Transactional(readOnly = true)
    public Group getGroup(Long groupId) {
        return groupRepository.findById(groupId).orElseThrow(
                () -> new BusinessException("Nhóm không tồn tại")
        );
    }


    @Override
    @Transactional
    public GroupResponse joinGroup(String shareToken) { // join through link

        //isLoggedIn("joinGroup");

        User user = userService.getCurrentUser();
        GroupUtils.TokenInfo tokenInfo = GroupUtils.parseToken(shareToken);
        Long groupId = tokenInfo.groupId();
        Group group = getGroup(groupId);

        if(group.getStatus().equals(GroupStatus.DELETED)) {
            throw new BusinessException("Nhóm đã bị xóa");
        }

        if(!group.getShareToken().equals(shareToken)){
            throw new BusinessException("Token không hợp lệ");
        }

        if(LocalDateTime.now().isAfter(group.getExpireAt())) {
            throw new BusinessException("Token đã hết hạn");
        }

        groupParticipantService.addUserToGroup(user, group, JoinGroupType.LINK);

//        group.setTotalMembers(group.getTotalMembers() + 1);
//        groupRepository.save(group);

        return groupMapper.toResponse(group, getLeaderFromGroup(group.getGroupId()));
    }


    @Override
    @Transactional
    public GroupResponse addUserToGroup(Long userId, Long groupId) { // leader add

        isLoggedIn("addUserToGroup");

        Group group = getGroup(groupId);
        User currentUser = userService.getCurrentUser();
        User addUser = userService.getUserById(userId);

        if(group.getStatus().equals(GroupStatus.DELETED)) {
            throw new BusinessException("Không thể add thành viên khi nhóm đã bị xóa");
        }

        if(!currentUser.equals(group.getCreatedBy())) {
            throw new GroupAuthorizeException("Chỉ có trưởng nhóm mới có thể add thành viên");
        }

        if(currentUser.getUserId().equals(addUser.getUserId())) {
            throw new BusinessException("Không thể add chính mình vào nhóm");
        }

        if (!validateFollowEachOther(currentUser, addUser)) {
            throw new BusinessException("Cả 2 phải theo dõi nhau để add vào group");
        }

        groupParticipantService.addUserToGroup(addUser, group, JoinGroupType.ADD);

//        group.setTotalMembers(group.getTotalMembers() + 1);
//        groupRepository.save(group);

        return groupMapper.toResponse(group, getLeaderFromGroup(group.getGroupId()));
    }


    @Override
    @Transactional
    public GroupResponse kickUserFromGroup(Long userId, Long groupId) {

        isLoggedIn("kickUserFromGroup");

        Group group = getGroup(groupId);
        User leader = userService.getCurrentUser();
        User member = userService.getUserById(userId);

        if(group.getStatus().equals(GroupStatus.DELETED)) {
            throw new BusinessException("Nhóm đã bị xóa");
        }

        if(!groupParticipantService.isLeader(leader, group)) {
            throw new GroupAuthorizeException("Chỉ có trưởng nhóm mới có thể kick thành viên");
        }

        if(member.getUserId().equals(leader.getUserId())) {
            throw new BusinessException("Bạn không thể kick chính mình");
        }

        if(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserId_AndAction(groupId, userId, GroupParticipantAction.KICKED)) {
            throw new BusinessException("Thành viên này đã bị kick khỏi nhóm");
        }

        groupParticipantService.updateAction(member, group, GroupParticipantAction.KICKED);

        group.setTotalMembers(countMember(group.getGroupId()));
        groupRepository.save(group);

        return groupMapper.toResponse(group, getLeaderFromGroup(group.getGroupId()));
    }


    @Override
    @Transactional
    public GroupResponse leaveGroup(Long groupId) {

        //isLoggedIn("leaveGroup");

        Group group = getGroup(groupId);
        User user = userService.getCurrentUser();

        if(group.getStatus().equals(GroupStatus.DELETED)) {
            throw new BusinessException("Nhóm đã bị xóa");
        }

        if(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserId_AndAction(groupId, user.getUserId(), GroupParticipantAction.PENDING)) {
            throw new BusinessException("Bạn không thể rời nhóm khi đang chờ duyệt. Hãy hủy yêu cầu tham gia nhóm");
        }

        if(!groupParticipantRepository.existsByGroup_GroupIdAndUser_UserId_AndAction(groupId, user.getUserId(), GroupParticipantAction.JOIN)) {
            throw new BusinessException("Bạn không thuộc nhóm này");
        }

        if(groupParticipantService.isLeader(user, group)) {
            throw new BusinessException("Trưởng nhóm không thể rời nhóm. Hãy chuyển quyền trưởng nhóm cho người khác trước khi rời nhóm");
        }

        groupParticipantService.updateAction(user, group, GroupParticipantAction.LEAVE);

        group.setTotalMembers(countMember(group.getGroupId()));
        groupRepository.save(group);

        return groupMapper.toResponse(group, getLeaderFromGroup(group.getGroupId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupParticipantResponse> getGroupParticipantsByAction(Long groupId, GroupParticipantAction action) {

        User user = userService.getCurrentUser();
        Group group = getGroup(groupId);

        if(group.getStatus().equals(GroupStatus.DELETED)) {
            throw new BusinessException("Nhóm đã bị xóa");
        }

        if(!groupParticipantRepository.existsByGroup_GroupIdAndUser_UserId_AndAction(groupId, user.getUserId(), GroupParticipantAction.JOIN)) {
            throw new GroupAuthorizeException("Người dùng không phải là thành viên của nhóm");
        }

        boolean isLeader = groupParticipantRepository.existsByGroup_GroupIdAndUser_UserIdAndRole(groupId, user.getUserId(), GroupRole.LEADER);

        if(action == null || !isLeader) {
            return groupParticipantService.getGroupParticipants(groupId).stream()
                    .filter(gp -> gp.getAction() == GroupParticipantAction.JOIN)
                    .map(groupParticipantMapper::toResponse)
                    .toList();
        } else {
            return groupParticipantService.getGroupParticipantByAction(groupId, action);
        }
    }

    @Override
    public GroupResponse refreshSharedToken(Long groupId) {

        isLoggedIn("refreshSharedToken");

        User user = userService.getCurrentUser();
        Group group = getGroup(groupId);
        GroupParticipant leaderGp = groupParticipantRepository.findByGroup_GroupIdAndUser_UserId(groupId, user.getUserId()).orElse(null);

        if(group.getStatus().equals(GroupStatus.DELETED)) {
            throw new BusinessException("Nhóm đã bị xóa");
        }

        if(leaderGp == null || leaderGp.getRole() != GroupRole.LEADER) {
            throw new GroupAuthorizeException("Chỉ có trưởng nhóm mới có thể tạo mới invite code");
        }

        String shareToken = GroupUtils.generateToken(group.getGroupId());
        LocalDateTime expireTime = LocalDateTime.now().plusDays(1); // expired after 24 hours

        if(shareToken.equals(group.getShareToken())) {
            throw new BusinessException("Gặp lỗi khi generate lại token. Hãy thử lại");
        }

        group.setExpireAt(expireTime);
        group.setShareToken(shareToken);
        groupRepository.save(group);

        return groupMapper.toResponse(group, getLeaderFromGroup(group.getGroupId()));
    }

    @Override
    @Transactional
    public GroupParticipantResponse updateGroupParticipantAction(Long groupParticipantId, GroupParticipantAction action) {
        return groupParticipantService.updateAction(groupParticipantId, action);
    }


    // for temporary, remove after implement authen
    private void isLoggedIn(String methodName) {
        boolean isLoggedIn = SecurityUtils.getCurrentUserKeyCloakId().isPresent();
        if (!isLoggedIn) {
            throw new BusinessException("Người dùng chưa đăng nhập: " + methodName);
        }
    }

    private Long getLeaderFromGroup(Long groupId) {
        GroupParticipant gp = groupParticipantRepository.findByGroup_GroupIdAndRole(groupId, GroupRole.LEADER);
        return gp != null ? gp.getUser().getUserId() : null;
    }

    private Integer countMember(long groupId) {
        return groupParticipantRepository.findAllByGroup_GroupIdAndAction(groupId, GroupParticipantAction.JOIN).size();
    }

    private List<User> validateListMember(Long leaderId, List<Long> memberIds, String groupName) {

        List<Long> valid = userFollowRepository.findMutualFollowerIds(leaderId, memberIds);

        if(valid == null || valid.isEmpty() || valid.size() < 1) {
            throw new BusinessException("Bạn và 1 thành viên cần phải theo dõi nhau để tạo nhóm");
        }

//          để show notification cho leader khi tạo nhóm
        List<Long> invalid = new ArrayList<>(memberIds);
        invalid.removeAll(valid);
        if(!invalid.isEmpty()) {
            pushNotiForInvalidMember(leaderId, invalid, groupName);
        }

        List<User> members = userService.getUsersByIds(valid);

        return members;
    }

    private boolean validateFollowEachOther(User followingUser, User followerUser) {
        return userFollowRepository.existsByFollowerAndFollowing(followerUser, followingUser)
                && userFollowRepository.existsByFollowerAndFollowing(followingUser, followerUser);
    }

    private void pushNotiForInvalidMember(Long leaderId, List<Long> invalidIds, String groupName) {
        User leader = userService.getUserById(leaderId);
        List<String> invalidUsername = userService.getUsersByIds(invalidIds).stream().map(User::getUsername).toList();
        StringBuilder stringBuilder = new StringBuilder();
        invalidUsername.forEach(username -> stringBuilder.append(username).append(", "));

        fcmService.sendPushNotification(
                leader.getFcmToken(),
                "Không thể add " + invalidIds.size() + " thành viên vào nhóm " + groupName,
                "Những người dùng sau không theo dõi bạn hoặc bạn không theo dõi họ: " + stringBuilder.toString() + "vì vậy không thể add vào nhóm",
                NotificationType.GROUP,
                leaderId
        );
    }
}
