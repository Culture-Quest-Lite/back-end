package org.sep490.backend.module.groupquest.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupParticipantAction;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupRole;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupStatus;

import java.time.LocalDateTime;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GroupParticipantResponse {
    Long groupParticipantId;
    Long userId;
    Long groupId;
    GroupRole role;
    GroupStatus status;
    GroupParticipantAction action;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
