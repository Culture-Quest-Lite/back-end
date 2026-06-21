package org.sep490.backend.module.content.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.enums.ContentStatus;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HotspotResponse {
    Long hotspotId;
    List<TagResponse> tags;
    Long createByUserId;
    String hotspotName;
    String address;
    String description;
    Double latitude;
    Double longitude;
    Double checkInRadius;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    Long xp;
    Long point;
    Long estimatedDurationMin;
    Long estimatedDurationMax;
    LocalTime startTime;
    LocalTime endTime;
    ContentStatus status;
}
