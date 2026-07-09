package org.sep490.backend.module.social.dto.request;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.social.entity.enumeration.PostVisibility;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ShareRequest {
    String content; 
    PostVisibility visibility = PostVisibility.PUBLIC; 
}
