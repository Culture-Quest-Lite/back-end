package org.sep490.backend.module.content.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoryResponse {
    Long storyId;
    TagResponse tag;
    Long hotspotId;
    Integer orderIndex;
    String title;
    String content;
    ContentStatus status;
    List<MediaResponse> medias;
}
