package org.sep490.backend.module.curator.dto.projection;

public interface HotspotCheckInCountProjection {
    Long getHotspotId();
    String getHotspotName();
    Long getCheckInCount();
}
