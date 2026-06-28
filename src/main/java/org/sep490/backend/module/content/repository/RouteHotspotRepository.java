package org.sep490.backend.module.content.repository;

import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.RouteHotspot;
import org.sep490.backend.module.exploration.dto.response.CheckInHotspotResponse;
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

    @Query("""
        SELECT new org.sep490.backend.module.exploration.dto.response.CheckInHotspotResponse(
            h.hotspotId,
            h.hotspotName,
            (CASE WHEN uc.checkInId IS NOT NULL THEN true ELSE false END),
            rh.index
        )
        FROM RouteHotspot rh
        JOIN rh.hotspot h
        LEFT JOIN CheckIn uc ON uc.hotspot.hotspotId = h.hotspotId AND uc.user.userId = :userId
        WHERE rh.route.routeId = :routeId
        ORDER BY rh.index ASC
    """)
    List<CheckInHotspotResponse> getHotspotCheckInStatusByRouteAndUser(
            @Param("routeId") Long routeId,
            @Param("userId") Long userId
    );

    List<RouteHotspot> findByHotspot_HotspotId(Long hotspotId);

    List<Long> findRouteIdsByHotspot_HotspotId(Long hotspotId);
}
