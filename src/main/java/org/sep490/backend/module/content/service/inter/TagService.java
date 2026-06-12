package org.sep490.backend.module.content.service.inter;

import org.sep490.backend.module.content.dto.request.TagFilterRequest;
import org.sep490.backend.module.content.dto.request.TagRequest;
import org.sep490.backend.module.content.dto.response.TagResponse;
import org.sep490.backend.module.content.entity.Tag;
import org.springframework.data.domain.Page;

public interface TagService {
    TagResponse create(TagRequest request);
    TagResponse update(Long id, TagRequest request);
    TagResponse getDetail(Long id);
    Page<TagResponse> getAllWithFilter(TagFilterRequest filter);
    void delete(Long id);
    Tag getById(Long id);
}
