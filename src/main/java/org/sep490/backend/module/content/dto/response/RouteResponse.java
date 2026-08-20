package org.sep490.backend.module.content.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.entity.enumeration.RouteDifficulty;
import org.sep490.backend.module.content.entity.enumeration.RouteStatus;
import org.sep490.backend.module.exploration.entity.enumuration.RouteParticipantStatus;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RouteResponse {
    Long routeId;
    String routeName;
    String description;
    String imageUrl;
    RouteDifficulty difficulty;
    Double estimateTime;
    Double totalDistance;
    RouteStatus status;
    Long xp;
    Long point;
    Double averageRating;
    Long totalReviews;
    RouteParticipantStatus userProgress;
    TagResponse tag;
    List<HotspotResponse> hotspots;
}
