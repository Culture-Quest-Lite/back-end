package org.sep490.backend.module.curator.dto.projection;

public interface RatingSummaryProjection {
    Double getAverageRating();
    Long getTotalReviews();
}
