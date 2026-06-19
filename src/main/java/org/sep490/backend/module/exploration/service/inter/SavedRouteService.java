package org.sep490.backend.module.exploration.service.inter;

import org.sep490.backend.module.exploration.dto.response.SavedRouteResponse;
import org.sep490.backend.module.exploration.entity.SavedRoute;

import java.util.List;

public interface SavedRouteService {
    SavedRouteResponse saveRoute(Long routeId);
    void unsaveRoute(Long savedRouteId);
    List<SavedRouteResponse> findAll();
    SavedRoute findById(Long savedRouteId);
}
