package org.sep490.backend.module.planner.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.dto.response.HotspotResponse;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlanHotspotResponse {
    Long planHotspotId;
    Integer stopIndex;
    String userNote;
    Boolean isCheckedIn;
    HotspotResponse hotspot;
}
