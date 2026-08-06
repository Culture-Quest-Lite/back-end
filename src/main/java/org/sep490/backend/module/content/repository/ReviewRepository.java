package org.sep490.backend.module.content.repository;

import org.sep490.backend.module.content.dto.projection.HotspotRatingSummaryProjection;
import org.sep490.backend.module.content.dto.projection.ReviewRatingCountProjection;
import org.sep490.backend.module.content.dto.projection.TargetRatingSummaryProjection;
import org.sep490.backend.module.content.entity.Review;
import org.sep490.backend.module.content.entity.enumeration.ReviewStatus;
import org.sep490.backend.module.curator.dto.projection.RatingSummaryProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long>, JpaSpecificationExecutor<Review> {

    Optional<Review> findByReviewIdAndStatusNot(Long reviewId, ReviewStatus status);

    boolean existsByUser_UserIdAndHotspot_HotspotIdAndStatusNot(Long userId, Long hotspotId, ReviewStatus status);

    boolean existsByUser_UserIdAndRoute_RouteIdAndStatusNot(Long userId, Long routeId, ReviewStatus status);

    boolean existsByUser_UserIdAndStory_StoryIdAndStatusNot(Long userId, Long storyId, ReviewStatus status);

    @Query("SELECT r.rating AS rating, COUNT(r) AS total FROM Review r " +
            "WHERE r.hotspot.hotspotId = :targetId AND r.status = :status " +
            "GROUP BY r.rating")
    List<ReviewRatingCountProjection> countRatingsByHotspotId(@Param("targetId") Long targetId,
                                                              @Param("status") ReviewStatus status);

    @Query("SELECT r.rating AS rating, COUNT(r) AS total FROM Review r " +
            "WHERE r.route.routeId = :targetId AND r.status = :status " +
            "GROUP BY r.rating")
    List<ReviewRatingCountProjection> countRatingsByRouteId(@Param("targetId") Long targetId,
                                                            @Param("status") ReviewStatus status);

    @Query("SELECT r.rating AS rating, COUNT(r) AS total FROM Review r " +
            "WHERE r.story.storyId = :targetId AND r.status = :status " +
            "GROUP BY r.rating")
    List<ReviewRatingCountProjection> countRatingsByStoryId(@Param("targetId") Long targetId,
                                                            @Param("status") ReviewStatus status);

    @Query("SELECT r.hotspot.hotspotId AS hotspotId, " +
            "AVG(r.rating) AS averageRating, " +
            "COUNT(r) AS totalReviews " +
            "FROM Review r " +
            "WHERE r.hotspot.hotspotId IN :hotspotIds AND r.status = :status " +
            "GROUP BY r.hotspot.hotspotId")
    List<HotspotRatingSummaryProjection> summarizeRatingsByHotspotIds(@Param("hotspotIds") List<Long> hotspotIds,
                                                                      @Param("status") ReviewStatus status);

    @Query("SELECT r.route.routeId AS targetId, " +
            "AVG(r.rating) AS averageRating, " +
            "COUNT(r) AS totalReviews " +
            "FROM Review r " +
            "WHERE r.route.routeId IN :routeIds AND r.status = :status " +
            "GROUP BY r.route.routeId")
    List<TargetRatingSummaryProjection> summarizeRatingsByRouteIds(@Param("routeIds") List<Long> routeIds,
                                                                   @Param("status") ReviewStatus status);

    @Query("SELECT r.story.storyId AS targetId, " +
            "AVG(r.rating) AS averageRating, " +
            "COUNT(r) AS totalReviews " +
            "FROM Review r " +
            "WHERE r.story.storyId IN :storyIds AND r.status = :status " +
            "GROUP BY r.story.storyId")
    List<TargetRatingSummaryProjection> summarizeRatingsByStoryIds(@Param("storyIds") List<Long> storyIds,
                                                                   @Param("status") ReviewStatus status);

    @Query("SELECT AVG(r.rating) AS averageRating, COUNT(r) AS totalReviews " +
            "FROM Review r " +
            "WHERE r.status = :status")
    RatingSummaryProjection summarizeGlobalRatings(@Param("status") ReviewStatus status);
}
