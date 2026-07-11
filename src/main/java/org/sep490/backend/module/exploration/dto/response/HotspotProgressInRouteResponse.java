package org.sep490.backend.module.exploration.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HotspotProgressInRouteResponse {
    Long userProgressId;
    Long userId;
    Long hotspotId;
    Boolean isCheckedIn;
    Integer index;
    Double latitude;
    Double longitude;
    Integer totalPointEarned;
    Integer totalXpEarned;
    LocalDateTime firstVisitedAt;
}
