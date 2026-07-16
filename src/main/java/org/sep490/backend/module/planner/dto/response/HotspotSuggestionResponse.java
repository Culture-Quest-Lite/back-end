package org.sep490.backend.module.planner.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.dto.response.HotspotResponse;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HotspotSuggestionResponse {
    HotspotResponse hotspot;
    Double score;
    String reason;
    Double distanceInMeters;
}
