package org.sep490.backend.module.content.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RouteHotspotRequest {
    @NotNull(message = "Hotspot ID không được để trống")
    Long hotspotId;

    @NotNull(message = "Thứ tự không được để trống")
    @PositiveOrZero(message = "Thứ tự phải bắt đầu từ 0 trở lên")
    Integer index;
}
