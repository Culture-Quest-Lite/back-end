package org.sep490.backend.module.planner.service;

import org.sep490.backend.module.planner.dto.request.OptimizeRouteRequest;
import org.sep490.backend.module.planner.dto.response.OptimizedRouteResponse;

public interface RouteOptimizationService {
    OptimizedRouteResponse optimize(OptimizeRouteRequest request);
}
