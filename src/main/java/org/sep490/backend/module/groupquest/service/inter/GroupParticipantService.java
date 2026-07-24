package org.sep490.backend.module.groupquest.service.inter;

import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.groupquest.dto.response.GroupParticipantResponse;
import org.sep490.backend.module.groupquest.entity.Group;
import org.sep490.backend.module.groupquest.entity.GroupParticipant;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupParticipantAction;
import org.sep490.backend.module.groupquest.entity.enumuration.JoinGroupType;

import java.util.List;

public interface
GroupParticipantService {
    GroupParticipant addUserToGroup(User user, Group group, JoinGroupType type);
    GroupParticipant addLeaderToGroup(User user, Group group);
    GroupParticipant updateAction(User user, Group group, GroupParticipantAction action);
    GroupParticipant getGroupParticipant(Long groupId, Long userId);
    GroupParticipant getGroupParticipant(Long groupParticipantId);
    List<GroupParticipant> getMyParticipants();
    List<GroupParticipant> getGroupParticipants(Long groupId);
    Boolean isLeader(User user, Group group);
    Boolean isParticipant(User user, Group group);
    List<GroupParticipantResponse> getGroupParticipantByAction(Long groupId, GroupParticipantAction action);
    GroupParticipantResponse updateAction(Long groupParticipantId, GroupParticipantAction action);
}
