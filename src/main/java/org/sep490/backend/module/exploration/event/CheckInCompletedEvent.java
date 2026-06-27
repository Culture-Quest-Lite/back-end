package org.sep490.backend.module.exploration.event;

import org.sep490.backend.module.gamification.entity.enumeration.ActionType;
import org.sep490.backend.module.gamification.entity.enumeration.TransactionType;

public record CheckInCompletedEvent(
        Long userId,
        Long point,
        Long xp,
        Long hotspotId,
        Long referenceId,
        TransactionType transactionType,
        String description,
        ActionType source
) {}
