package org.sep490.backend.module.groupquest.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.utils.GroupUtils;
import org.sep490.backend.common.utils.SecurityUtils;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.groupquest.dto.request.GroupRequest;
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
import org.sep490.backend.module.user.repository.UserFollowRepository;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    @Override
    @Transactional
    public GroupResponse createGroup(GroupRequest request) {

        isLoggedIn("createGroup");

        User user = userService.getCurrentUser();
        Group group = Group.builder()
                .createdBy(user)
                .groupName(request.getGroupName())
                .totalMembers(1)
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

        return groupMapper.toResponse(group);
    }


    @Override
    @Transactional
    public GroupResponse updateGroup(Long groupId, GroupRequest request) {

        Group group = getGroup(groupId);

        group.setGroupName(request.getGroupName());

        groupRepository.save(group);

        return groupMapper.toResponse(group);
    }


    @Override
    @Transactional
    public GroupResponse deleteGroup(Long groupId) {

        Group group = getGroup(groupId);

        group.setStatus(GroupStatus.DELETED);

        groupRepository.save(group);
        // implement update GP.action to dismiss

        return groupMapper.toResponse(group);
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
                .map(groupMapper::toResponse)
                .collect(Collectors.toList());
    }


    @Override
    @Transactional(readOnly = true)
    public GroupResponse getDetails(Long groupId) {

        Group group = getGroup(groupId);

        return groupMapper.toResponse(group);
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

        isLoggedIn("joinGroup");

        User user = userService.getCurrentUser();
        GroupUtils.TokenInfo tokenInfo = GroupUtils.parseToken(shareToken);
        Long groupId = tokenInfo.groupId();
        Group group = getGroup(groupId);

        if(LocalDateTime.now().isAfter(group.getExpireAt())) {
            throw new BusinessException("Token đã hết hạn");
        }

        groupParticipantService.addUserToGroup(user, group, JoinGroupType.LINK);

        group.setTotalMembers(group.getTotalMembers() + 1);
        groupRepository.save(group);

        return groupMapper.toResponse(group);
    }


    @Override
    @Transactional
    public GroupResponse addUserToGroup(Long userId, Long groupId) { // leader add

        isLoggedIn("addUserToGroup");

        Group group = getGroup(groupId);
        User currentUser = userService.getCurrentUser();
        User addUser = userService.getUserById(userId);

        if(!currentUser.equals(group.getCreatedBy())) {
            throw new BusinessException("Chỉ có trưởng nhóm mới có thể add thành viên");
        }

        if(!currentUser.getUserId().equals(addUser.getUserId())) {
            throw new BusinessException("Không thể add chính mình vào nhóm");
        }

        boolean isFollowed = userFollowRepository.existsByFollowerAndFollowing(currentUser, addUser)
                && userFollowRepository.existsByFollowerAndFollowing(addUser, currentUser);

        if(!isFollowed) {
            throw new BusinessException("Cả 2 phải theo dõi nhau để add vào group");
        }

        groupParticipantService.addUserToGroup(addUser, group, JoinGroupType.ADD);

        group.setTotalMembers(group.getTotalMembers() + 1);
        groupRepository.save(group);

        return groupMapper.toResponse(group);
    }


    @Override
    @Transactional
    public GroupResponse kickUserFromGroup(Long userId, Long groupId) {

        isLoggedIn("kickUserFromGroup");

        Group group = getGroup(groupId);
        User leader = userService.getCurrentUser();
        User member = userService.getUserById(userId);

        if(!groupParticipantService.isLeader(leader, group)) {
            throw new BusinessException("Chỉ có trưởng nhóm mới có thể kick thành viên");
        }

        groupParticipantService.updateAction(member, group, GroupParticipantAction.KICKED);

        group.setTotalMembers(group.getTotalMembers() - 1);
        groupRepository.save(group);

        return groupMapper.toResponse(group);
    }


    @Override
    @Transactional
    public GroupResponse leaveGroup(Long groupId) {

        isLoggedIn("leaveGroup");

        Group group = getGroup(groupId);
        User user = userService.getCurrentUser();

        if(!groupParticipantService.isParticipant(user, group)) {
            throw new BusinessException("Người dùng không phải là thành viên của nhóm");
        }

        groupParticipantService.updateAction(user, group, GroupParticipantAction.KICKED);

        group.setTotalMembers(group.getTotalMembers() - 1);
        groupRepository.save(group);

        return groupMapper.toResponse(group);
    }


    @Override
    @Transactional(readOnly = true)
    public List<GroupParticipantResponse> getGroupParticipants(Long groupId) {

        return groupParticipantService.getGroupParticipants(groupId).stream()
                .filter(gp -> gp.getAction() == GroupParticipantAction.JOIN)
                .map(groupParticipantMapper::toResponse)
                .toList();
    }

    @Override
    public GroupResponse refreshSharedToken(Long groupId) {

        isLoggedIn("refreshSharedToken");

        User user = userService.getCurrentUser();
        Group group = getGroup(groupId);

        if(!group.getCreatedBy().equals(user)) {
            throw new BusinessException("Chỉ có trưởng nhóm mới có thể tạo mới invite code");
        }

        String shareToken = GroupUtils.generateToken(group.getGroupId());
        LocalDateTime expireTime = LocalDateTime.now().plusDays(1); // expired after 24 hours

        group.setExpireAt(expireTime);
        group.setShareToken(shareToken);
        groupRepository.save(group);

        return groupMapper.toResponse(group);
    }


    // for temporary, remove after implement authen
    private void isLoggedIn(String methodName) {
        boolean isLoggedIn = SecurityUtils.getCurrentUserKeyCloakId().isPresent();
        if (!isLoggedIn) {
            throw new BusinessException("Người dùng chưa đăng nhập: " + methodName);
        }
    }
}
