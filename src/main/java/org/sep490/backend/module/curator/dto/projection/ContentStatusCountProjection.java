package org.sep490.backend.module.curator.dto.projection;

import org.sep490.backend.module.content.entity.enumeration.ContentStatus;

public interface ContentStatusCountProjection {
    ContentStatus getStatus();
    Long getTotal();
}
