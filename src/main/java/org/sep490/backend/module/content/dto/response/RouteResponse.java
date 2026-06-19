package org.sep490.backend.module.content.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.enums.ContentStatus;
import org.sep490.backend.module.content.enums.RouteDifficulty;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RouteResponse {
    Long routeId;
    String routeName;
    String description;
    RouteDifficulty difficulty;
    Double estimateTime;
    Double totalDistance;
    ContentStatus status;
    Long xp;
    List<TagResponse> tags;
    List<RouteHotspotResponse> hotspots;
}
