package org.sep490.backend.module.content.dto.projection;

public interface TargetRatingSummaryProjection {

    Long getTargetId();

    Double getAverageRating();

    Long getTotalReviews();
}
