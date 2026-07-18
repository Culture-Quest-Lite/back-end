package org.sep490.backend.module.planner.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalTime;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OptimizedStopResponse {
    Long hotspotId;
    Integer index;
    String hotspotName;
    Double latitude;
    Double longitude;
    Double distanceToNext;
    Double travelTimeToNext;
    String travelTimeToNextText;

    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(type = "string")
    LocalTime estimatedArrivalTime;

    Boolean closingWarning;

}
