package org.sep490.backend.module.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReportReviewRequest {
    @NotBlank(message = "Vui lòng cung cấp lý do báo cáo")
    private String comment;
}
