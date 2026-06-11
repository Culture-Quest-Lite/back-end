package org.sep490.backend.module.content.dto.request;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.enums.RouteDifficulty;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RouteRequest {
    String routeName;
    String description;
    RouteDifficulty difficulty;
    Double estimateTime;
    Double totalDistance;
    List<RouteHotspotRequest> hotspots;
    List<Long> categoryIds;
}
