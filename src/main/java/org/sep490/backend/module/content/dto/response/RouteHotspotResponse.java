package org.sep490.backend.module.content.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RouteHotspotResponse {
    Long routeHotspotId;
    Long routeId;
    Long hotspotId;
    Integer index;
    Double distanceToNext;
}
