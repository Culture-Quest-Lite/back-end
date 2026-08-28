package org.sep490.backend.module.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class HandleReportReviewRequest {
    private List<Long> reviewActionIds;
    private Boolean isApproveReport;
    @NotBlank(message = "Bạn phải cung cấp lý do xử lý")
    private String reason;
}
