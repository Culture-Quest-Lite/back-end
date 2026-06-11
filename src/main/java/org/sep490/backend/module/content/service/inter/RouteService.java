package org.sep490.backend.module.content.service.inter;

import org.sep490.backend.module.content.dto.request.RouteRequest;
import org.sep490.backend.module.content.dto.response.RouteResponse;
import org.sep490.backend.module.content.entity.Route;

import java.util.List;

public interface RouteService {
    RouteResponse create(RouteRequest request);
    RouteResponse update(Long id, RouteRequest request);
    RouteResponse getDetail(Long id);
    void delete(Long id);
    Route getById(Long id);
}
