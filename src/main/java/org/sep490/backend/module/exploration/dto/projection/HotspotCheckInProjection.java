package org.sep490.backend.module.exploration.dto.projection;

import java.time.LocalDateTime;

public interface HotspotCheckInProjection {
    Long getUserProgressId();
    Long getUserId();
    Long getHotspotId();
    Boolean getIsCheckedIn();
    Integer getIndex();
    Double getLongitude();
    Double getLatitude();
    Integer getTotalPointEarned();
    Integer getTotalXpEarned();
    LocalDateTime getFirstVisitedAt();
}
