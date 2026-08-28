package org.sep490.backend.module.exploration.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserHotspotProgressRequest {
    @NotNull(message = "Hotspot ID không được để trống")
    Long hotspotId;

    @NotNull(message = "Vĩ độ không được để trống")
    Double latitude;

    @NotNull(message = "Kinh độ không được để trống")
    Double longitude;

    @PositiveOrZero(message = "Độ chính xác GPS không hợp lệ")
    @Schema(description = "Sai số GPS (mét) do thiết bị báo về, lấy từ coords.accuracy", example = "12.5")
    Double accuracy;
}
