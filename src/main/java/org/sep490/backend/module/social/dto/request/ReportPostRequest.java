package org.sep490.backend.module.social.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReportPostRequest {
    @NotBlank(message = "Bạn cần cung cấp lý do báo cáo bài viết này")
    String comment;
}
