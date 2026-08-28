package org.sep490.backend.module.exploration.repository;

import org.sep490.backend.module.exploration.entity.SavedRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SavedRouteRepository extends JpaRepository<SavedRoute, Long> {
    Optional<SavedRoute> findByRoute_RouteIdAndUser_UserId(Long routeRouteId, Long userUserId);

    boolean  existsByRoute_RouteIdAndUser_UserId(Long routeRouteId, Long userId);

    List<SavedRoute> findAllByUser_UserId(Long userId);

    @Query("SELECT p.user.userId FROM UserPlan p WHERE p.userPlanId = :id")
    Optional<Long> findOwnerId(@Param("id") Long id);
}
