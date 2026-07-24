package org.sep490.backend.module.groupquest.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.groupquest.dto.response.GroupParticipantResponse;
import org.sep490.backend.module.groupquest.entity.Group;
import org.sep490.backend.module.groupquest.entity.GroupParticipant;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupParticipantAction;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupRole;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupStatus;
import org.sep490.backend.module.groupquest.entity.enumuration.JoinGroupType;
import org.sep490.backend.module.groupquest.mapper.GroupParticipantMapper;
import org.sep490.backend.module.groupquest.repository.GroupParticipantRepository;
import org.sep490.backend.module.groupquest.service.inter.GroupParticipantService;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GroupParticipantServiceImpl implements GroupParticipantService {

    GroupParticipantRepository repository;
    UserService userService;
    GroupParticipantMapper mapper;

    @Override
    @Transactional
    public GroupParticipant addUserToGroup(User user, Group group, JoinGroupType type) {

        GroupParticipant groupParticipant = new GroupParticipant();

        GroupParticipantAction action;

        if(repository.existsByGroup_GroupIdAndUser_UserId_AndAction(group.getGroupId(), user.getUserId(), GroupParticipantAction.JOIN)) {
            throw new BusinessException("Người dùng đã là thành viên của nhóm");
        }

        if(group.getRequiredApproval() && type.equals(JoinGroupType.LINK)) {
            action = GroupParticipantAction.PENDING;
        } else {
            action = GroupParticipantAction.JOIN;
        }

        if(!repository.existsByGroup_GroupIdAndUser_UserId(group.getGroupId(), user.getUserId())) { // join lần đầu
            groupParticipant.setUser(user);
            groupParticipant.setGroup(group);
            groupParticipant.setRole(GroupRole.MEMBER);
            groupParticipant.setAction(action);
            groupParticipant.setStatus(GroupStatus.ACTIVE);
        } else {
            groupParticipant = getGroupParticipant(group.getGroupId(), user.getUserId());
            if(groupParticipant.getAction() != GroupParticipantAction.JOIN) {
                groupParticipant.setAction(action);
                groupParticipant.setRole(GroupRole.MEMBER);
                groupParticipant.setStatus(GroupStatus.ACTIVE);
            }
        }

        return repository.save(groupParticipant);
    }

    @Override
    @Transactional
    public GroupParticipant addLeaderToGroup(User user, Group group) {

        GroupParticipant groupParticipant = GroupParticipant.builder()
                .user(user)
                .group(group)
                .role(GroupRole.LEADER)
                .action(GroupParticipantAction.JOIN)
                .status(GroupStatus.ACTIVE)
                .build();

        return repository.save(groupParticipant);
    }

    @Override
    @Transactional
    public GroupParticipant updateAction(User user, Group group, GroupParticipantAction action) {

        if(!repository.existsByGroup_GroupIdAndUser_UserId(group.getGroupId(), user.getUserId())) {
            throw new BusinessException("Người dùng chưa là thành viên của nhóm");
        }

        GroupParticipant groupParticipant = getGroupParticipant(group.getGroupId(), user.getUserId());

        groupParticipant.setAction(action);

        return repository.save(groupParticipant);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupParticipant getGroupParticipant(Long groupId, Long userId) {
        return repository.findByGroup_GroupIdAndUser_UserId(groupId, userId).orElseThrow(
                () -> new BusinessException("Người dùng không phải là thành viên của nhóm")
        );
    }

    @Override
    @Transactional(readOnly = true)
    public GroupParticipant getGroupParticipant(Long groupParticipantId) {
        return repository.findById(groupParticipantId).orElseThrow(
                () -> new BusinessException("Thành viên nhóm không tồn tại")
        );
    }

    @Override
    public List<GroupParticipant> getMyParticipants() {
        User user = userService.getCurrentUser();

        return repository.findAllByUser_UserIdAndActionAndStatus(
                user.getUserId(),
                GroupParticipantAction.JOIN,
                GroupStatus.ACTIVE);
    }

    @Override
    public List<GroupParticipant> getGroupParticipants(Long groupId) {
        return repository.findAllByGroup_GroupId(groupId);
    }

    @Override
    public Boolean isLeader(User user, Group group) {
        GroupParticipant gp = getGroupParticipant(group.getGroupId(), user.getUserId());
        return gp.getRole() == GroupRole.LEADER;
    }

    @Override
    public Boolean isParticipant(User user, Group group) {
        return repository.existsByGroup_GroupIdAndUser_UserId(group.getGroupId(), user.getUserId());
    }

    @Override
    public List<GroupParticipantResponse> getGroupParticipantByAction(Long groupId, GroupParticipantAction action) {
        return repository.findAllByGroup_GroupIdAndAction(groupId, action).stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public GroupParticipantResponse updateAction(Long groupParticipantId, GroupParticipantAction action) {
        GroupParticipant participant = getGroupParticipant(groupParticipantId);
        Group group = participant.getGroup();
        User user = participant.getUser();

        if(repository.existsByGroup_GroupIdAndUser_UserIdAndRole(group.getGroupId(), user.getUserId(), GroupRole.LEADER)) {
            throw new BusinessException("Bạn không phải là trưởng nhóm");
        }

        participant.setAction(action);
        participant = repository.save(participant);

        return mapper.toResponse(participant);
    }
}
