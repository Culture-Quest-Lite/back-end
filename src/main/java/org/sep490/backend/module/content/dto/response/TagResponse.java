package org.sep490.backend.module.content.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.entity.enumeration.TagStatus;

import java.time.LocalDateTime;
import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TagResponse {
    Long tagId;
    String tagName;
    String imageUrl;
    TagStatus tagStatus;
    Long routeCount;
    Long hotspotCount;
    Long storyCount;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    List<TagUsageResponse> usages;
}
