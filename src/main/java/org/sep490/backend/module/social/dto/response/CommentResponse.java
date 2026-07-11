package org.sep490.backend.module.social.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CommentResponse {
    Long postActionId;
    Long postId;
    Long userId;
    String username;
    String displayName;
    String comment;
    LocalDateTime createdAt;
    List<CommentResponse> replies;
}
