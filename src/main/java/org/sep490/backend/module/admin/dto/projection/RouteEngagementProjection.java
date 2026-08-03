package org.sep490.backend.module.admin.dto.projection;

public interface RouteEngagementProjection {
    Long getRouteId();
    String getRouteName();
    Long getStartedCount();
    Long getCompletedCount();
}
