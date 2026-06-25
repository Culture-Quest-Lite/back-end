package org.sep490.backend.module.gamification.dto.request;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.gamification.entity.enumeration.XpSource;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class XpHistoryRequest {
    Long userId;
    Long xpAmount;
    XpSource source;
    Long referenceId;
    String description;
}
