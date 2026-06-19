package org.sep490.backend.module.partner.dto.filter;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.partner.entity.enumeration.VoucherStatus;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VoucherFilter {
    String search;
    VoucherStatus status;
    Long partnerId;

    @Min(value = 0, message = "Page không được nhỏ hơn 0")
    int page = 0;

    @Min(value = 1, message = "Size phải ít nhất là 1")
    @Max(value = 100, message = "Size không được vượt quá 100")
    int size = 10;

    String sortBy = "createdAt";
    String sortDir = "desc";
}
