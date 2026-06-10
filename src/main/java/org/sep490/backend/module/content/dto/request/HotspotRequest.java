package org.sep490.backend.module.content.dto.request;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HotspotRequest {
    List<Long> categoryIds;
    String hotspotName;
    String address;
    String description;
    Double latitude;
    Double longitude;
    Double checkInRadius;
}
