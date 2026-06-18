package org.sep490.backend.module.exploration.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckInHotspotResponse {
    Long hotspotId;
    String hotspotName;
    boolean isCheckedIn;
    Integer index;
}
