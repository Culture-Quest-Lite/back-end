package org.sep490.backend.module.exploration.repository;

import org.sep490.backend.module.exploration.entity.UserRouteProgress;
import org.sep490.backend.module.exploration.entity.enumuration.ProgressStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface UserRouteProgressRepository extends JpaRepository<UserRouteProgress, Long>, JpaSpecificationExecutor<UserRouteProgress> {
    Optional<UserRouteProgress> findByRoute_RouteIdAndUser_UserId(Long routeId, Long userId);

    List<UserRouteProgress> findByUser_UserIdAndRoute_RouteIdInAndStatusNot(Long userId, List<Long> routeIds, ProgressStatus status);
}
