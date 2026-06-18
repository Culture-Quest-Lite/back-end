package org.sep490.backend.module.exploration.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckInHotspotResponse {
    Long hotspotId;
    String hotspotName;
    Boolean isCheckedIn;
    Integer index;
}
