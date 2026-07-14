package org.sep490.backend.module.partner.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VoucherUsageResponse {
    Long voucherUsageId;
    Long voucherId;
    String voucherCode;
    String voucherName;
    String description;
    Long pointsRequired;
    LocalDateTime redeemedAt;
    LocalDateTime usedAt;
    LocalDateTime expiredAt;
    Boolean isUsed;
}
