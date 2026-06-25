package org.sep490.backend.module.content.repository;

import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.enumeration.RouteStatus;
import org.sep490.backend.module.content.entity.enumeration.RouteType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface RouteRepository extends JpaRepository<Route, Long>, JpaSpecificationExecutor<Route> {
    Integer countByCreatedBy(User creator);
    Optional<Route> findByCreatedByAndTypeAndStatus(User creator, RouteType routeType, RouteStatus routeStatus);
}
