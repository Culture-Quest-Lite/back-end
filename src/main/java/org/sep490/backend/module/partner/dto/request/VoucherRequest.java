package org.sep490.backend.module.partner.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.partner.entity.enumeration.DiscountType;
import org.sep490.backend.module.partner.entity.enumeration.VoucherStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class VoucherRequest {
    String voucherCode;

    @NotBlank(message = "Tên voucher không được để trống")
    String voucherName;

    String description;

    @NotNull(message = "Loại giảm giá là bắt buộc")
    DiscountType discountType;

    @NotNull(message = "Giá trị giảm giá là bắt buộc")
    @Positive(message = "Giá trị giảm giá phải là số dương")
    BigDecimal discountValue;

    BigDecimal maxDiscountAmount;
    BigDecimal minOrderAmount;

    @NotNull(message = "Điểm yêu cầu là bắt buộc")
    @Min(value = 0, message = "Điểm yêu cầu không được là số âm")
    Long pointsRequired;

    @NotNull(message = "Số lượng tổng cộng là bắt buộc")
    @Min(value = 1, message = "Số lượng tổng cộng phải ít nhất là 1")
    Long quantityTotal;

    VoucherStatus status;

    @NotNull(message = "Ngày bắt đầu là bắt buộc")
    LocalDateTime startDate;

    @NotNull(message = "Ngày kết thúc là bắt buộc")
    LocalDateTime endDate;
}
