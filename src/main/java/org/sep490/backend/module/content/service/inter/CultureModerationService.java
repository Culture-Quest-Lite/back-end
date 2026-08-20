package org.sep490.backend.module.content.service.inter;

import org.sep490.backend.module.content.dto.request.CultureRejectRequest;
import org.sep490.backend.module.content.dto.response.CultureContentResponse;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.dto.response.TagResponse;

public interface CultureModerationService {

    CultureContentResponse getPending();

    CultureContentResponse getRejected();

    TagResponse approveTag(Long tagId);

    TagResponse rejectTag(Long tagId, CultureRejectRequest request);

    StoryResponse approveStory(Long storyId);

    StoryResponse rejectStory(Long storyId, CultureRejectRequest request);
}
