package org.sep490.backend.module.exploration.dto.request;

import jakarta.validation.constraints.NotNull;
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
}
