package org.sep490.backend.module.gamification.dto.request;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.gamification.entity.enumeration.TransactionType;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PointTransactionRequest {
    Long userId;
    Long pointAmount;
    Long balanceRemaining;
    Long referenceId;
    TransactionType transactionType;
    String description;
}
