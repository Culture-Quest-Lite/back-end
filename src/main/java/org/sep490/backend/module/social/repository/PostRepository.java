package org.sep490.backend.module.social.repository;

import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.social.entity.Post;
import org.sep490.backend.module.social.entity.enumeration.PostStatus;
import org.sep490.backend.module.social.entity.enumeration.PostVisibility;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    @Query("SELECT p FROM Post p WHERE (CAST(:status AS string) IS NULL OR p.status = :status)")
    Slice<Post> findByStatusOptional(@Param("status") PostStatus status, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.status = :status AND p.visibility = :visibility " +
            "ORDER BY p.createdAt DESC")
    Slice<Post> findByStatusAndVisibility(@Param("status") PostStatus status,
                                          @Param("visibility") PostVisibility visibility,
                                          Pageable pageable);

    @Deprecated
    @Query("SELECT p FROM Post p " +
            "WHERE p.status = :status " +
            "ORDER BY CASE WHEN p.user IN (SELECT f.following FROM UserFollow f WHERE f.follower = :currentUser) THEN 0 ELSE 1 END ASC, " +
            "p.createdAt DESC")
    Slice<Post> findNewsfeed(@Param("currentUser") User currentUser,
                             @Param("status") PostStatus status,
                             Pageable pageable);

    @Query("SELECT f.following.userId FROM UserFollow f WHERE f.follower.userId = :followerId")
    List<Long> findFollowingIds(@Param("followerId") Long followerId);

    @Query("SELECT p FROM Post p WHERE p.status = :status AND p.visibility = :visibility " +
            "AND p.user.userId IN :authorIds " +
            "AND (CAST(:cursor AS timestamp) IS NULL OR p.createdAt < :cursor) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findFeedByAuthors(@Param("status") PostStatus status,
                                 @Param("visibility") PostVisibility visibility,
                                 @Param("authorIds") List<Long> authorIds,
                                 @Param("cursor") LocalDateTime cursor,
                                 Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.status = :status AND p.visibility = :visibility " +
            "AND p.user.userId NOT IN :excludedAuthorIds " +
            "AND (CAST(:cursor AS timestamp) IS NULL OR p.createdAt < :cursor) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findFeedExcludingAuthors(@Param("status") PostStatus status,
                                        @Param("visibility") PostVisibility visibility,
                                        @Param("excludedAuthorIds") List<Long> excludedAuthorIds,
                                        @Param("cursor") LocalDateTime cursor,
                                        Pageable pageable);

    Slice<Post> findByUser_UserIdAndStatus(Long userId, PostStatus status, Pageable pageable);

    @Query("SELECT DISTINCT p FROM Post p JOIN p.taggedHotspots h " +
            "WHERE h.hotspotId = :hotspotId AND p.status = :status " +
            "ORDER BY p.createdAt DESC")
    Slice<Post> findByHotspotIdAndStatus(@Param("hotspotId") Long hotspotId,
                                         @Param("status") PostStatus status,
                                         Pageable pageable);

    long countByUser(User user);

    long countByStatus(PostStatus status);

    @Query("SELECT p.user.userId FROM Post p WHERE p.postId = :id")
    Optional<Long> findOwnerId(@Param("id") Long id);
}
