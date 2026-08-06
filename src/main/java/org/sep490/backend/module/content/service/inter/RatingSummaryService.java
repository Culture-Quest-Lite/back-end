package org.sep490.backend.module.content.service.inter;

import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.content.dto.response.RouteResponse;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.entity.enumeration.ReviewTargetType;

import java.util.List;

public interface RatingSummaryService {

    HotspotResponse applyToHotspot(HotspotResponse response);

    void applyToHotspots(List<HotspotResponse> responses);

    RouteResponse applyToRoute(RouteResponse response);

    void applyToRoutes(List<RouteResponse> responses);

    StoryResponse applyToStory(StoryResponse response);

    void applyToStories(List<StoryResponse> responses);

    void evict(ReviewTargetType targetType, Long targetId);
}
