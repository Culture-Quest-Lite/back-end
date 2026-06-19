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
public class PointTransactionResponse {
    Long id;
    Integer pointAmount;
    TransactionType transactionType;
    String description;
    Long balanceRemaining;
    Long referenceId;
    LocalDateTime createdAt;
}
