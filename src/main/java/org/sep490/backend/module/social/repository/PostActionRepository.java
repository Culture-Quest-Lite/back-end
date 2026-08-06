package org.sep490.backend.module.social.repository;

import org.sep490.backend.module.social.entity.PostAction;
import org.sep490.backend.module.social.entity.enumeration.PostActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostActionRepository extends JpaRepository<PostAction, Long> {
    Optional<PostAction> findByPost_PostIdAndUser_UserIdAndActionType(Long postId, Long userId, PostActionType actionType);
    Slice<PostAction> findByPost_PostIdAndActionTypeAndParentActionIsNullOrderByCreatedAtAsc(Long postId, PostActionType actionType, Pageable pageable);

    /**
     * Đếm like/comment/share của một post bằng MỘT query GROUP BY,
     * thay cho việc nạp toàn bộ collection postActions rồi stream đếm.
     */
    @Query("SELECT a.actionType, COUNT(a) FROM PostAction a " +
            "WHERE a.post.postId = :postId GROUP BY a.actionType")
    List<Object[]> countActionsByPostId(@Param("postId") Long postId);

    /** Kiểm tra user đã like post chưa — thay cho việc stream cả collection. */
    boolean existsByPost_PostIdAndUser_UserIdAndActionType(
            Long postId, Long userId, PostActionType actionType);
}
