package org.sep490.backend.module.social.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.social.entity.enumeration.PostVisibility;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostRequest {
    @NotBlank(message = "Nội dung bài viết không được để trống")
    String content;

    PostVisibility visibility = PostVisibility.PUBLIC;

    List<Long> hotspotIds;
    List<Long> routeIds;
    List<Long> tagIds;
}
