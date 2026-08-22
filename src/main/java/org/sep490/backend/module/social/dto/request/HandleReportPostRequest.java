package org.sep490.backend.module.social.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HandleReportPostRequest {
    List<Long> postActionIds;
    Boolean isApproveReport;
    @NotBlank(message = "Bạn cần cung cấp lý do xử lý báo cáo bài viết này")
    String reason;
}
