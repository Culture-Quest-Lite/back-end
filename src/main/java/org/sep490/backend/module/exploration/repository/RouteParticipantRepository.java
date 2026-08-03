package org.sep490.backend.module.exploration.repository;

import org.sep490.backend.module.admin.dto.projection.RouteEngagementProjection;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.enumeration.RouteStatus;
import org.sep490.backend.module.exploration.entity.RouteParticipant;
import org.sep490.backend.module.exploration.entity.enumuration.ProgressStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RouteParticipantRepository extends JpaRepository<RouteParticipant, Long>, JpaSpecificationExecutor<RouteParticipant> {

    Optional<RouteParticipant> findByRoute_RouteIdAndUser_UserId(Long routeId, Long userId);

    List<RouteParticipant> findByUser_UserIdAndRoute_RouteIdInAndStatusNot(
            Long userId, List<Long> routeIds, ProgressStatus status);

    List<RouteParticipant> findByUserInAndStatus(List<User> users, ProgressStatus status);

    List<RouteParticipant> findByUserInAndRoute(List<User> users, Route route);

    @Query("SELECT r.routeId AS routeId, " +
            "r.routeName AS routeName, " +
            "COUNT(rp) AS startedCount, " +
            "COALESCE(SUM(CASE WHEN rp.status = :completedStatus THEN 1L ELSE 0L END), 0L) AS completedCount " +
            "FROM RouteParticipant rp " +
            "JOIN rp.route r " +
            "WHERE r.status <> :deletedStatus " +
            "GROUP BY r.routeId, r.routeName " +
            "ORDER BY COUNT(rp) DESC, r.routeId ASC")
    List<RouteEngagementProjection> findTopRouteEngagement(@Param("completedStatus") ProgressStatus completedStatus,
                                                           @Param("deletedStatus") RouteStatus deletedStatus,
                                                           Pageable pageable);
}
