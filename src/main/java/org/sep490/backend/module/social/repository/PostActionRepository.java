package org.sep490.backend.module.social.repository;

import org.sep490.backend.module.social.entity.PostAction;
import org.sep490.backend.module.social.entity.enumeration.PostActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import java.util.Optional;

@Repository
public interface PostActionRepository extends JpaRepository<PostAction, Long> {
    Optional<PostAction> findByPost_PostIdAndUser_UserIdAndActionType(Long postId, Long userId, PostActionType actionType);
    Slice<PostAction> findByPost_PostIdAndActionTypeAndParentActionIsNullOrderByCreatedAtAsc(Long postId, PostActionType actionType, Pageable pageable);
}
