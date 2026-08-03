package org.sep490.backend.module.content.dto.projection;

/**
 * Thống kê đánh giá gộp theo hotspot, dùng để tránh N+1 khi trả về danh sách hotspot.
 */
public interface HotspotRatingSummaryProjection {
    Long getHotspotId();
    Double getAverageRating();
    Long getTotalReviews();
}
