package org.sep490.backend.module.exploration.service.inter;

import org.sep490.backend.module.exploration.dto.request.CheckInRequest;
import org.sep490.backend.module.exploration.dto.response.CheckInResponse;

public interface CheckInService {
    CheckInResponse checkIn(CheckInRequest checkInRequest);
}
