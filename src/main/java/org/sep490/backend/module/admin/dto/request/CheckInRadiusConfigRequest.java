package org.sep490.backend.module.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.exploration.service.impl.CheckInPolicy;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckInRadiusConfigRequest {

    @NotNull(message = "Bán kính tối thiểu không được để trống")
    @Min(value = 1, message = "Bán kính tối thiểu phải lớn hơn 0")
    @Max(value = CheckInPolicy.ABSOLUTE_MAX_RADIUS_METERS,
            message = "Bán kính tối thiểu không được vượt quá " + CheckInPolicy.ABSOLUTE_MAX_RADIUS_METERS + "m")
    @Schema(description = "Bán kính check-in nhỏ nhất curator được phép đặt (mét)", example = "20")
    Integer minRadius;

    @NotNull(message = "Bán kính tối đa không được để trống")
    @Min(value = 1, message = "Bán kính tối đa phải lớn hơn 0")
    @Max(value = CheckInPolicy.ABSOLUTE_MAX_RADIUS_METERS,
            message = "Bán kính tối đa không được vượt quá " + CheckInPolicy.ABSOLUTE_MAX_RADIUS_METERS + "m")
    @Schema(description = "Bán kính check-in lớn nhất curator được phép đặt (mét)", example = "5000")
    Integer maxRadius;

    @NotNull(message = "Bán kính mặc định không được để trống")
    @Min(value = 1, message = "Bán kính mặc định phải lớn hơn 0")
    @Max(value = CheckInPolicy.ABSOLUTE_MAX_RADIUS_METERS,
            message = "Bán kính mặc định không được vượt quá " + CheckInPolicy.ABSOLUTE_MAX_RADIUS_METERS + "m")
    @Schema(description = "Bán kính áp dụng khi curator không nhập (mét)", example = "50")
    Integer defaultRadius;
}
