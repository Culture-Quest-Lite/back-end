package org.sep490.backend.module.partner.dto.filter;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.partner.entity.enumeration.VoucherStatus;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdvanceVoucherFilter {
    Long routeId;
    List<Long> hotspotIds;

    @NotNull(message = "Khoảng cách không được để trống")
    @Min(value = 0, message = "Khoảng cách không được nhỏ hơn 0")
    Double distanceMeters;

    @NotNull(message = "Trạng thái voucher không được để trống")
    VoucherStatus status;

    Double longitude;
    Double latitude;

    @Min(value = 0, message = "Page không được nhỏ hơn 0")
    int page = 0;

    @Min(value = 1, message = "Size phải ít nhất là 1")
    @Max(value = 100, message = "Size không được vượt quá 100")
    int size = 10;
}
