package org.sep490.backend.module.groupquest.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupStatus;

import java.time.LocalDateTime;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GroupResponse {
    Long groupId;
    Long createdBy;
    Integer totalMembers;
    String shareToken;
    GroupStatus status;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
