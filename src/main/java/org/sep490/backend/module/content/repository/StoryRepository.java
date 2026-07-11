package org.sep490.backend.module.content.repository;

import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.exploration.dto.projection.HotspotCheckInProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StoryRepository extends JpaRepository<Story, Long>, JpaSpecificationExecutor<Story> {

    Collection<Story> findByHotspot_HotspotId(Long hotspotId);

    Integer countByHotspot_HotspotId(Long hotspotId);

    List<Story> findByRoute_RouteIdOrderByOrderIndexAsc(Long routeId);

    List<Story> findByRoute_RouteId(Long routeId);

    void deleteByRoute_RouteId(Long routeId);

    @Query("SELECT s.hotspot FROM Story s " +
            "WHERE s.route.routeId = :routeId " +
            "ORDER BY s.orderIndex ASC")
    List<Hotspot> findHotspotsByRouteIdOrderByIndexAsc(@Param("routeId") Long routeId);

    @Query("SELECT DISTINCT s.route.routeId FROM Story s " +
            "WHERE s.hotspot.hotspotId = :hotspotId " +
            "AND s.route IS NOT NULL")
    List<Long> findRouteIdsByHotspot_HotspotId(@Param("hotspotId") Long hotspotId);

    @Query(value = """
        SELECT
            uhp.user_progress_id AS userProgressId,
            uhp.user_id AS userId,
            h.hotspot_id AS hotspotId,
            CASE WHEN uhp.user_progress_id IS NOT NULL THEN true ELSE false END AS isCheckedIn,
            s.order_index AS index,
            ST_X(uhp.location) AS longitude,
            ST_Y(uhp.location) AS latitude,
            uhp.total_point_earned AS totalPointEarned,
            uhp.total_xp_earned AS totalXpEarned,
            uhp.first_visited_at AS firstVisitedAt
        FROM stories s
        JOIN hotspots h ON s.hotspot_id = h.hotspot_id
        LEFT JOIN user_hotspot_progress uhp
            ON uhp.hotspot_id = h.hotspot_id AND uhp.user_id = :userId
        WHERE s.route_id = :routeId
        ORDER BY s.order_index ASC
    """, nativeQuery = true)
    List<HotspotCheckInProjection> getHotspotCheckInStatusByRouteAndUserNative(
            @Param("routeId") Long routeId,
            @Param("userId") Long userId
    );

    @Query("""
        SELECT s FROM Story s
        WHERE s.hotspot.hotspotId = :hotspotId
        ORDER BY
            (CASE WHEN s.tag.tagId IN :routeTagIds THEN 0 ELSE 1 END) ASC,
            s.orderIndex ASC
    """)
    List<Story> findByHotspotOrderedByRouteTag(
            @Param("hotspotId") Long hotspotId,
            @Param("routeTagIds") List<Long> routeTagIds
        );

    @Query("SELECT s FROM Story s WHERE s.hotspot.hotspotId = :hotspotId " +
            "ORDER BY s.orderIndex ASC")
    List<Story> findByHotspotOrderedByIndex(@Param("hotspotId") Long hotspotId);

    @Query("SELECT DISTINCT s.tag.tagId FROM Story s WHERE s.route.routeId = :routeId")
    List<Long> findTagIdsByRouteId(@Param("routeId") Long routeId);
}
