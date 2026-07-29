package org.sep490.backend.module.content.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.entity.enumeration.ReviewTargetType;

import java.util.Map;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReviewSummaryResponse {
    ReviewTargetType targetType;
    Long targetId;
    Double averageRating;
    Long totalReviews;
    Map<Integer, Long> ratingDistribution;
}
