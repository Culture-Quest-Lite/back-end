package org.sep490.backend.module.social.repository;

import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.social.entity.Post;
import org.sep490.backend.module.social.entity.enumeration.PostStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {
    @Query("SELECT p FROM Post p WHERE (:status IS NULL OR p.status = :status)")
    Slice<Post> findByStatusOptional(@Param("status") PostStatus status, Pageable pageable);

    @Query("SELECT p FROM Post p " +
            "WHERE p.status = :status " +
            "ORDER BY CASE WHEN p.user IN (SELECT f.following FROM UserFollow f WHERE f.follower = :currentUser) THEN 0 ELSE 1 END ASC, " +
            "p.createdAt DESC")
    Slice<Post> findNewsfeed(@Param("currentUser") User currentUser,
                             @Param("status") PostStatus status,
                             Pageable pageable);

    Slice<Post> findByUser_UserIdAndStatus(Long userId, PostStatus status, Pageable pageable);
}
