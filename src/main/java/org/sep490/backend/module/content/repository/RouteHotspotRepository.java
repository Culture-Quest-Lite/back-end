package org.sep490.backend.module.content.repository;

import org.sep490.backend.module.content.entity.RouteHotspot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteHotspotRepository extends JpaRepository<RouteHotspot, Long> {
    void deleteByRoute_RouteId(Long routeId);
    List<RouteHotspot> findByRoute_RouteIdOrderByIndexAsc(Long routeId);
}
