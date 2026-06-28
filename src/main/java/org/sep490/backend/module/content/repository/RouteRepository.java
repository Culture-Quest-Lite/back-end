package org.sep490.backend.module.content.repository;

import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.enumeration.RouteStatus;
import org.sep490.backend.module.content.entity.enumeration.RouteType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RouteRepository extends JpaRepository<Route, Long>, JpaSpecificationExecutor<Route> {
    Integer countByCreatedBy(User creator);
    Optional<Route> findByCreatedByAndTypeAndStatus(User creator, RouteType routeType, RouteStatus routeStatus);

    @Query("SELECT rh.route FROM RouteHotspot rh WHERE rh.hotspot.hotspotId = :hotspotId AND rh.route.status = :status")
    List<Route> findRoutesByHotspotIdAndStatus(@Param("hotspotId") Long hotspotId, @Param("status") RouteStatus status);
}
