package org.sep490.backend.module.gamification.dto.request;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.gamification.entity.enumeration.ActionType;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class XpHistoryRequest {
    Long userId;
    Long xpAmount;
    ActionType source;
    Long referenceId;
    String description;
}
