package org.sep490.backend.module.content.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HotspotResponse {
    Long hotspotId;
    List<CategoryResponse> categories;
    Long createByUserId;
    String hotspotName;
    String address;
    String description;
    Double latitude;
    Double longitude;
    Double checkInRadius;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
