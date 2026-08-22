package org.sep490.backend.module.content.repository;

import org.sep490.backend.module.content.entity.ReviewAction;
import org.sep490.backend.module.content.entity.enumeration.ReviewActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ReviewActionRepository extends JpaRepository<ReviewAction, Long> {
    Optional<ReviewAction> findByReview_ReviewIdAndUser_UserIdAndActionType(Long reviewId, Long userId, ReviewActionType actionType);

    /**
     * Đếm like cho NHIỀU review trong một query.
     * Tránh N+1 khi render một trang review (trước đây mỗi review nạp cả collection reviewActions).
     */
    @Query("SELECT a.review.reviewId, COUNT(a) FROM ReviewAction a " +
            "WHERE a.review.reviewId IN :reviewIds AND a.actionType = :actionType " +
            "GROUP BY a.review.reviewId")
    List<Object[]> countActionsByReviewIds(@Param("reviewIds") List<Long> reviewIds,
                                           @Param("actionType") ReviewActionType actionType);

    /** Các review trong danh sách mà user đã like — một query cho cả trang. */
    @Query("SELECT a.review.reviewId FROM ReviewAction a " +
            "WHERE a.user.userId = :userId AND a.review.reviewId IN :reviewIds " +
            "AND a.actionType = :actionType")
    Set<Long> findLikedReviewIds(@Param("userId") Long userId,
                                 @Param("reviewIds") List<Long> reviewIds,
                                 @Param("actionType") ReviewActionType actionType);

    Integer countByActionTypeAndReview_ReviewIdAndIsReportResolved(
            ReviewActionType actionType, Long reviewId, Boolean isReportResolved);

    List<ReviewAction> findByActionTypeAndIsReportResolved(
            ReviewActionType actionType, Boolean isReportResolved);

    List<ReviewAction> findByActionTypeAndUser_UserIdAndIsReportResolved(
            ReviewActionType actionType, Long userId, Boolean isReportResolved);

    Optional<ReviewAction> findByReview_ReviewIdAndUser_UserIdAndActionTypeAndIsReportResolved(Long reviewId, Long userId, ReviewActionType actionType,Boolean isReportResolved);
}
