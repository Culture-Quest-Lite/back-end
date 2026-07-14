package org.sep490.backend.module.exploration.event;

public record RouteProgressCompletedEvent(
        Long userId,
        Long routeId
) {}
