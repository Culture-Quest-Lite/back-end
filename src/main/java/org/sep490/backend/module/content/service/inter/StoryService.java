package org.sep490.backend.module.content.service.inter;

import org.sep490.backend.module.content.dto.filter.StoryFilterRequest;
import org.sep490.backend.module.content.dto.request.StoryRequest;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.springframework.data.domain.Page;

import java.util.List;

public interface StoryService {
    StoryResponse create(StoryRequest storyRequest);
    StoryResponse update(Long id, StoryRequest storyRequest);
    StoryResponse getDetail(Long id);
    List<StoryResponse> getByHotspot(Long hotspotId);
    void delete(Long id);
    Story getById(Long id);
    Page<StoryResponse> getAll(StoryFilterRequest filter);
    StoryResponse updateStatus(Long id, ContentStatus status);
}
