package org.sep490.backend.module.social.service;

import org.sep490.backend.module.social.dto.response.PostResponse;

/**
 * Đếm like/comment/share của bài viết, có cache bằng Redis Hash.
 *
 * PostgreSQL vẫn là nguồn sự thật — cache miss thì đếm lại bằng một query GROUP BY
 * rồi nạp đủ cả 3 field, không bao giờ tăng dần từ trạng thái thiếu.
 */
public interface PostCounterService {

    /** Gán likeCount / commentCount / shareCount cho response. */
    void apply(PostResponse response, Long postId);

    /** Xoá counter cache — gọi sau khi like/comment/share thay đổi. */
    void evict(Long postId);
}
