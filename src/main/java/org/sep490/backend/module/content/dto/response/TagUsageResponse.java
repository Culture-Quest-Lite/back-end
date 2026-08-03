package org.sep490.backend.module.content.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.entity.enumeration.TagUsageType;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TagUsageResponse {
    Long refId;
    TagUsageType type; // STORY, ROUTE
}
