package org.sep490.backend.module.content.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.entity.enumeration.ReviewStatus;
import org.sep490.backend.module.content.entity.enumeration.ReviewTargetType;

import java.time.LocalDateTime;
import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReviewResponse {
    Long reviewId;
    Long userId;
    String username;
    String displayName;
    String avatarUrl;
    ReviewTargetType targetType;
    Long targetId;
    Integer rating;
    String comment;
    ReviewStatus status;
    List<MediaResponse> medias;
    //Luôn được set trong ReviewServiceImpl.toResponse nên @JsonInclude(NON_NULL) không ẩn mất
    Long likeCount;
    Boolean isLiked;
    Boolean isOwner;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
