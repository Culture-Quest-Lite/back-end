package org.sep490.backend.module.curator.dto.projection;

import org.sep490.backend.module.content.entity.enumeration.RouteStatus;

public interface RouteStatusCountProjection {
    RouteStatus getStatus();
    Long getTotal();
}
