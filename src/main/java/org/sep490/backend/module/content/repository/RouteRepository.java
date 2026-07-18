package org.sep490.backend.module.content.repository;

import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.dto.projection.TagRouteCountProjection;
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

    List<Route> findAllByCreatedByAndTypeAndStatus(User creator, RouteType routeType, RouteStatus routeStatus);

    List<Route> findAllByCreatedByAndType(User creator, RouteType routeType);

    @Query("SELECT DISTINCT s.route FROM Story s WHERE s.hotspot.hotspotId = :hotspotId AND s.route.status = :status AND s.route IS NOT NULL")
    List<Route> findRoutesByHotspotIdAndStatus(@Param("hotspotId") Long hotspotId, @Param("status") RouteStatus status);

    long countByTag_TagIdAndStatusNot(Long tagId, RouteStatus status);

    @Query("SELECT r.tag.tagId AS tagId, COUNT(r) AS routeCount FROM Route r " +
            "WHERE r.tag.tagId IN :tagIds AND r.status <> :excludedStatus " +
            "GROUP BY r.tag.tagId")
    List<TagRouteCountProjection> countRoutesByTagIds(@Param("tagIds") List<Long> tagIds,
                                                      @Param("excludedStatus") RouteStatus excludedStatus);
}
