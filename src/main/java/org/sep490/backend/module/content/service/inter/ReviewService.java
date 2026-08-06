package org.sep490.backend.module.content.service.inter;

import org.sep490.backend.module.content.dto.filter.ReviewFilterRequest;
import org.sep490.backend.module.content.dto.request.ReviewRequest;
import org.sep490.backend.module.content.dto.request.ReviewUpdateRequest;
import org.sep490.backend.module.content.dto.response.ReviewResponse;
import org.sep490.backend.module.content.dto.response.ReviewSummaryResponse;
import org.sep490.backend.module.content.entity.Review;
import org.sep490.backend.module.content.entity.enumeration.ReviewStatus;
import org.sep490.backend.module.content.entity.enumeration.ReviewTargetType;
import org.springframework.data.domain.Page;

public interface ReviewService {
    ReviewResponse createReview(ReviewRequest reviewRequest);

    ReviewResponse updateReview(Long id, ReviewUpdateRequest reviewRequest);

    ReviewResponse getReviewDetail(Long id);

    Page<ReviewResponse> getAllReview(ReviewFilterRequest filter);

    Page<ReviewResponse> getMyReviews(ReviewFilterRequest filter);

    ReviewSummaryResponse getSummary(ReviewTargetType targetType, Long targetId);

    ReviewResponse updateReviewStatus(Long id, ReviewStatus status);

    ReviewResponse toggleLikeReview(Long id);

    void deleteReview(Long id);

    Review getReviewById(Long id);
}
