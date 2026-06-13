package org.sep490.backend.module.content.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.enums.ContentStatus;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoryResponse {
    Long storyId;
    TagResponse tag;
    Long hotspotId;
    Integer orderIndex;
    String title;
    String content;
    String audioUrl;
    ContentStatus status;
}
