package org.sep490.backend.module.planner.service;

import org.sep490.backend.module.planner.dto.request.DescriptionSuggestRequest;
import org.sep490.backend.module.planner.dto.request.NearbySuggestRequest;
import org.sep490.backend.module.planner.dto.response.HotspotSuggestionResponse;

import java.util.List;

public interface AISuggestionService {
    List<HotspotSuggestionResponse> suggestByDescription(DescriptionSuggestRequest request);
    List<HotspotSuggestionResponse> suggestNearby(NearbySuggestRequest request);
}
