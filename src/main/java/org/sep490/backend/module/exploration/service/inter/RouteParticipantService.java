package org.sep490.backend.module.exploration.service.inter;

import org.sep490.backend.module.exploration.dto.filter.RouteParticipantFilter;
import org.sep490.backend.module.exploration.dto.response.RouteParticipantDetailResponse;
import org.sep490.backend.module.exploration.dto.response.RouteParticipantResponse;
import org.sep490.backend.module.exploration.entity.RouteParticipant;
import org.springframework.data.domain.Page;

import java.util.HashMap;

public interface RouteParticipantService {
    HashMap<Integer, RouteParticipantResponse> startRouteProgress(Long routeId);
    RouteParticipantResponse abandonRouteProgress(Long routeId);
    Page<RouteParticipantResponse> getAll(RouteParticipantFilter filter);
    RouteParticipantDetailResponse getRouteProgress(Long progressId);
    RouteParticipant getById(Long progressId);
    HashMap<Integer, RouteParticipantResponse> joinRouteFromLink(String token);
}
