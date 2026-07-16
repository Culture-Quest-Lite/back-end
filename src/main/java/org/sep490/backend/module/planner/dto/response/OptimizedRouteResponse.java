package org.sep490.backend.module.planner.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.planner.entity.enumeration.OptimizeCriterion;

import java.util.List;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OptimizedRouteResponse {
    List<OptimizedStopResponse> stops;
    Double totalDistance;
    Double totalEstimatedTime;
    String totalEstimatedTimeText;
    OptimizeCriterion criterion;
    Boolean usedFallback;
}
