package org.sep490.backend.module.exploration.event;

public record CheckInCustomRouteCompletedEvent (
        Long routeId,
        Long createdById
) {}
