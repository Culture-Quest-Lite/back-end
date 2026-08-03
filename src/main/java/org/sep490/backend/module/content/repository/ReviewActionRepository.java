package org.sep490.backend.module.content.repository;

import org.sep490.backend.module.content.entity.ReviewAction;
import org.sep490.backend.module.content.entity.enumeration.ReviewActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewActionRepository extends JpaRepository<ReviewAction, Long> {
    Optional<ReviewAction> findByReview_ReviewIdAndUser_UserIdAndActionType(Long reviewId, Long userId, ReviewActionType actionType);
}
