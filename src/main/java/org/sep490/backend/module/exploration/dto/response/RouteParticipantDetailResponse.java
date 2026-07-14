package org.sep490.backend.module.exploration.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.exploration.entity.enumuration.ProgressStatus;

import java.time.LocalDateTime;
import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RouteParticipantDetailResponse {
    Long routeParticipantId;
    Long routeId;
    String routeName;
    Integer totalStops;
    Integer completedStops;
    Double progressPercentage;
    ProgressStatus status;
    LocalDateTime startedAt;
    List<HotspotProgressInRouteResponse> hotspotProgressList;
}
