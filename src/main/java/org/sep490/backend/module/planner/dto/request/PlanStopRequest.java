package org.sep490.backend.module.planner.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlanStopRequest {

    @NotNull(message = "HotspotId không được để trống")
    Long hotspotId;
    String userNote;
}
