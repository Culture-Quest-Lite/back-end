package org.sep490.backend.module.exploration.repository;

import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.exploration.entity.RouteParticipant;
import org.sep490.backend.module.exploration.entity.enumuration.ProgressStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface RouteParticipantRepository extends JpaRepository<RouteParticipant, Long>, JpaSpecificationExecutor<RouteParticipant> {

    Optional<RouteParticipant> findByRoute_RouteIdAndUser_UserId(Long routeId, Long userId);

    List<RouteParticipant> findByUser_UserIdAndRoute_RouteIdInAndStatusNot(
            Long userId, List<Long> routeIds, ProgressStatus status);

    List<RouteParticipant> findByUserInAndStatus(List<User> users, ProgressStatus status);

    List<RouteParticipant> findByUserInAndRoute(List<User> users, Route route);
}
