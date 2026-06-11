package org.sep490.backend.module.content.repository;

import org.sep490.backend.module.content.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RouteRepository extends JpaRepository<Route, Long>, JpaSpecificationExecutor<Route> {
}
