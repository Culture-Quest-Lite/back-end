package org.sep490.backend.module.exploration.service.inter;

import org.sep490.backend.module.exploration.dto.request.UserHotspotProgressRequest;
import org.sep490.backend.module.exploration.dto.response.CheckInEligibilityResponse;
import org.sep490.backend.module.exploration.dto.response.UserHotspotProgressResponse;

public interface UserHotspotProgressService {
    UserHotspotProgressResponse checkIn(UserHotspotProgressRequest request);

    CheckInEligibilityResponse checkEligibility(Long hotspotId, Double latitude,
                                                Double longitude, Double accuracy);
}
