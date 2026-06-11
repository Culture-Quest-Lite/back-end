package org.sep490.backend.module.content.repository;

import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.RouteHotspot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RouteHotspotRepository extends JpaRepository<RouteHotspot, Long> {
    void deleteByRoute_RouteId(Long routeId);
    List<RouteHotspot> findByRoute_RouteIdOrderByIndexAsc(Long routeId);

    @Query("SELECT rh.hotspot FROM RouteHotspot rh " +
            "WHERE rh.route.routeId = :routeId " +
            "ORDER BY rh.index ASC")
    List<Hotspot> findHotspotsByRouteIdOrderByIndexAsc(@Param("routeId") Long routeId);

    Optional<RouteHotspot> findTopByRoute_RouteIdOrderByIndexDesc(Long routeId);

    List<RouteHotspot> findByRoute_RouteId(Long routeId);
}
