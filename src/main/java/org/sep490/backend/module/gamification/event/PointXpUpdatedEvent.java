package org.sep490.backend.module.gamification.event;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.gamification.entity.enumeration.TransactionType;
import org.sep490.backend.module.gamification.entity.enumeration.XpSource;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PointXpUpdatedEvent {
    Long userId;
    Long point;
    Long xp;
    Long hotspotId;
    Long referenceId;
    TransactionType transactionType;
    String description;
    XpSource source;
}
