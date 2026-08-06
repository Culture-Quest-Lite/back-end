package org.sep490.backend.module.social.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReportPostResponse {
    Long postActionId;
    Long postId;
    Long createdUserId;
    String createdDisplayName;
    String comment;
    LocalDateTime createdAt;
}
