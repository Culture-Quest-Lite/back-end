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

public interface PostRepository extends JpaRepository<Post, Long> {
    @Query("SELECT p FROM Post p WHERE (:status IS NULL OR p.status = :status)")
    Slice<Post> findByStatusOptional(@Param("status") PostStatus status, Pageable pageable);

    /**
     * Bản có lọc visibility để KHỚP index idx_post_feed_flow (status, visibility, created_at).
     * Chỉ lọc status thôi thì Postgres không dùng được index -> Seq Scan toàn bảng.
     * Đo trên 1.22 triệu dòng: Seq Scan 1348ms vs Index Scan 2.38ms.
     */
    @Query("SELECT p FROM Post p WHERE p.status = :status AND p.visibility = :visibility " +
            "ORDER BY p.createdAt DESC")
    Slice<Post> findByStatusAndVisibility(@Param("status") PostStatus status,
                                          @Param("visibility") PostVisibility visibility,
                                          Pageable pageable);

    /**
     * @deprecated ORDER BY CASE WHEN là biểu thức nên index không dùng được, buộc quét
     * và sort toàn bảng (7.7 giây ở trang 1000 với 1.22 triệu bài).
     * Dùng {@link #findFeedByAuthors} + {@link #findFeedExcludingAuthors} rồi ghép ở service.
     */
    @Deprecated
    @Query("SELECT p FROM Post p " +
            "WHERE p.status = :status " +
            "ORDER BY CASE WHEN p.user IN (SELECT f.following FROM UserFollow f WHERE f.follower = :currentUser) THEN 0 ELSE 1 END ASC, " +
            "p.createdAt DESC")
    Slice<Post> findNewsfeed(@Param("currentUser") User currentUser,
                             @Param("status") PostStatus status,
                             Pageable pageable);

    /** Id những người mà user đang theo dõi — lấy một lần, không lồng trong ORDER BY. */
    @Query("SELECT f.following.userId FROM UserFollow f WHERE f.follower.userId = :followerId")
    List<Long> findFollowingIds(@Param("followerId") Long followerId);

    /**
     * Phần 1 của newsfeed: bài của những người đang theo dõi.
     * Keyset pagination — truyền createdAt của bài cuối trang trước thay vì OFFSET.
     * OFFSET 10000 buộc Postgres duyệt rồi bỏ 10.000 dòng đầu; keyset thì nhảy thẳng.
     */
    @Query("SELECT p FROM Post p WHERE p.status = :status AND p.visibility = :visibility " +
            "AND p.user.userId IN :authorIds " +
            "AND (:cursor IS NULL OR p.createdAt < :cursor) " +
            "ORDER BY p.createdAt DESC")
    List<Post> findFeedByAuthors(@Param("status") PostStatus status,
                                 @Param("visibility") PostVisibility visibility,
                                 @Param("authorIds") List<Long> authorIds,
                                 @Param("cursor") LocalDateTime cursor,
                                 Pageable pageable);

    /** Phần 2 của newsfeed: bài của những người CHƯA theo dõi. */
    @Query("SELECT p FROM Post p WHERE p.status = :status AND p.visibility = :visibility " +
            "AND p.user.userId NOT IN :excludedAuthorIds " +
            "AND (:cursor IS NULL OR p.createdAt < :cursor) " +
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
}
