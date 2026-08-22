package org.sep490.backend.module.content.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportReviewResponse {
    private Long reviewActionId;
    private Long reviewId;
    private Long createdUserId;
    private String createdDisplayName;
    private String comment;
    private LocalDateTime createdAt;
}
