package org.sep490.backend.module.partner.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.partner.entity.enumeration.DiscountType;
import org.sep490.backend.module.partner.entity.enumeration.VoucherStatus;
import org.sep490.backend.module.content.dto.response.MediaResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VoucherResponse {
    Long voucherId;
    List<MediaResponse> medias;
    Long partnerId;
    String partnerName;
    String voucherCode;
    String voucherName;
    String description;
    DiscountType discountType;
    BigDecimal discountValue;
    BigDecimal maxDiscountAmount;
    BigDecimal minOrderAmount;
    Long pointsRequired;
    Long quantityTotal;
    Long quantityRemaining;
    VoucherStatus status;
    LocalDateTime startDate;
    LocalDateTime endDate;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
