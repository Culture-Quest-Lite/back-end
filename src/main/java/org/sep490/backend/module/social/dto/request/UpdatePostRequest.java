package org.sep490.backend.module.social.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.entity.Media;
import org.sep490.backend.module.social.entity.enumeration.PostVisibility;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdatePostRequest {
    @NotBlank(message = "Nội dung bài viết không được để trống")
    @Size(max = 5000, message = "Nội dung bài viết không được vượt quá 5000 ký tự")
    String content;

    List<Media> medias;

    PostVisibility visibility;
}
