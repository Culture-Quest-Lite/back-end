package org.sep490.backend.module.content.service.inter;

import org.sep490.backend.module.content.dto.request.HotspotRequest;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.content.entity.Hotspot;

import java.util.List;

public interface HotspotService {
    HotspotResponse create(HotspotRequest request);
    HotspotResponse update(Long id, HotspotRequest request);
    HotspotResponse getDetail(Long id);
    List<HotspotResponse> getAll();
    void delete(Long id);
    Hotspot getById(Long id);
}
