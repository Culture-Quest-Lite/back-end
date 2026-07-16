package org.sep490.backend.module.content.service.inter;

import org.sep490.backend.common.filter.dto.SearchRequest;
import org.sep490.backend.module.content.dto.request.RouteCreateRequest;
import org.sep490.backend.module.content.dto.request.RouteRequest;
import org.sep490.backend.module.content.dto.response.RouteResponse;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.enumeration.RouteStatus;
import org.springframework.data.domain.Page;

import java.util.List;

public interface RouteService {
    RouteResponse create(RouteRequest request);
    RouteResponse createV2(RouteCreateRequest request);
    RouteResponse update(Long id, RouteRequest request);
    RouteResponse getDetail(Long id);
    void delete(Long id);
    Route getById(Long id);
    Page<RouteResponse> filterRoutes(SearchRequest request);
    RouteResponse addHotspotToEndOfRoute(Long routeId, Long hotspotId);
    RouteResponse removeHotspotFromRoute(Long routeId, Long hotspotId);
    RouteResponse recordJourney();
    RouteResponse finishRecordJourney();
    Route findRecordingCustomRouteByUserId(Long userId);
    List<RouteResponse> getByHotspotId(Long hotspotId, RouteStatus routeStatus);
    RouteResponse addHotspotToEndOfCustomRoute(Long routeId, Long hotspotId, Long userId);
    RouteResponse finalizeCustomRoute(Long routeId);
}
