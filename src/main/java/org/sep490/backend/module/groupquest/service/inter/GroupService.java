package org.sep490.backend.module.groupquest.service.inter;

import org.sep490.backend.module.groupquest.dto.request.GroupRequest;
import org.sep490.backend.module.groupquest.dto.request.GroupUpdateRequest;
import org.sep490.backend.module.groupquest.dto.response.GroupParticipantResponse;
import org.sep490.backend.module.groupquest.dto.response.GroupResponse;
import org.sep490.backend.module.groupquest.entity.Group;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupParticipantAction;

import java.util.List;

public interface GroupService {
    GroupResponse createGroup(GroupRequest request);
    GroupResponse updateGroup(Long groupId, GroupUpdateRequest request);
    GroupResponse deleteGroup(Long groupId);
    List<GroupResponse> getMyGroups();
    GroupResponse getDetails(Long groupId);
    Group getGroup(Long groupId);
    GroupResponse joinGroup(String shareToken);
    GroupResponse addUserToGroup(Long userId, Long groupId);
    GroupResponse kickUserFromGroup(Long userId, Long groupId);
    GroupResponse leaveGroup(Long groupId);
    List<GroupParticipantResponse> getGroupParticipantsByAction(Long groupId, GroupParticipantAction action);
    GroupResponse refreshSharedToken(Long groupId);
    GroupParticipantResponse updateGroupParticipantAction(Long groupParticipantId, GroupParticipantAction action);
}

