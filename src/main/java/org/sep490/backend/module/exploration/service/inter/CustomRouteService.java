package org.sep490.backend.module.exploration.service.inter;

import org.sep490.backend.module.content.dto.request.FinalizeCustomRouteRequest;
import org.sep490.backend.module.content.dto.response.RouteResponse;
import org.sep490.backend.module.content.entity.enumeration.RouteStatus;

import java.util.List;

public interface CustomRouteService {
    RouteResponse recordJourney();
    RouteResponse finishRecordJourney();
    RouteResponse finalizeCustomRoute(FinalizeCustomRouteRequest request);
    List<RouteResponse> getMyJourney(RouteStatus routeStatus);
}
