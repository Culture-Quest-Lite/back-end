package org.sep490.backend.module.content.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.enums.TagStatus;

import java.time.LocalDateTime;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TagResponse {
    Long tagId;
    String tagName;
    TagStatus tagStatus;
    Long hotspotCount;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
