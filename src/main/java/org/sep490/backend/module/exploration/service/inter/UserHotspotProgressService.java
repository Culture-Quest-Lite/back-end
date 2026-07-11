package org.sep490.backend.module.exploration.service.inter;

import org.sep490.backend.module.exploration.dto.request.UserHotspotProgressRequest;
import org.sep490.backend.module.exploration.dto.response.UserHotspotProgressResponse;

public interface UserHotspotProgressService {
    UserHotspotProgressResponse checkIn(UserHotspotProgressRequest request);
}
