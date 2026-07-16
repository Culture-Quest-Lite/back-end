package org.sep490.backend.module.content.dto.request;

import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.web.multipart.MultipartFile;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoryRequest {
    MultipartFile[] files;

    @NotNull(message = "Tag ID không được để trống")
    Long tagId;

    @NotNull(message = "Hotspot ID không được để trống")
    Long hotspotId;

    @NotBlank(message = "Tiêu đề cốt truyện không được để trống")
    @Size(max = 100, message = "Tiêu đề không được vượt quá 100 ký tự")
    String title;

    @NotBlank(message = "Nội dung cốt truyện không được để trống")
    String content;

    String audioScript;
}
