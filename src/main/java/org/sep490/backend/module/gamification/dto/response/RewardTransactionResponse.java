package org.sep490.backend.module.gamification.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.gamification.entity.enumeration.TransactionType;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RewardTransactionResponse {
    Long transactionId;
    Long pointsAmount;
    Long xpAmount;
    TransactionType transactionType;
    String description;
    Long pointsBalance;
    Long xpBalance;
    Long referenceId;
    LocalDateTime createdAt;
}
