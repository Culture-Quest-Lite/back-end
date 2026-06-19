package org.sep490.backend.module.exploration.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckInResponse {
    Long checkInId;
    Long hotspotId;
    Long userRouteProgressId;
    Long pointEarned;
    Long xpEarned;
    LocalDateTime checkInAt;
}
