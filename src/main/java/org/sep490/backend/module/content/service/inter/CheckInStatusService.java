package org.sep490.backend.module.content.service.inter;

import org.sep490.backend.module.content.dto.response.HotspotResponse;

import java.util.List;

public interface CheckInStatusService {

    HotspotResponse apply(HotspotResponse response);

    void apply(List<HotspotResponse> responses);

    void addCheckedIn(Long userId, Long hotspotId);
}
