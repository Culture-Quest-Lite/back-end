package org.sep490.backend.module.planner.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.planner.entity.enumeration.PlanStatus;

import java.time.LocalDateTime;
import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserPlanResponse {
    Long userPlanId;
    String name;
    String description;
    PlanStatus status;
    Integer totalStops;
    Double startLatitude;
    Double startLongitude;
    Boolean isOptimized;
    LocalDateTime startedAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    Integer completedStops;
    Double progressPercentage;

    List<PlanHotspotResponse> stops;
}
