package org.sep490.backend.module.social.service.impl;

import org.sep490.backend.module.social.service.PostCounterService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.config.redis.CacheNames;
import org.sep490.backend.config.redis.RedisCircuitBreaker;
import org.sep490.backend.module.social.dto.response.PostResponse;
import org.sep490.backend.module.social.entity.enumeration.PostActionType;
import org.sep490.backend.module.social.repository.PostActionRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Đếm like/comment/share của post, cache bằng Redis Hash.
 *
 * NGUYÊN TẮC: PostgreSQL vẫn là nguồn sự thật. Redis chỉ là lớp đọc.
 * Cache miss thì đếm lại từ DB bằng MỘT query GROUP BY rồi nạp đủ cả 3 field.
 *
 * Vì sao DEL chứ không HINCRBY khi có thay đổi:
 * HINCRBY lên một key đang miss sẽ TẠO counter bắt đầu từ 0 và sai vĩnh viễn.
 * Muốn HINCRBY an toàn phải kiểm tra tồn tại + tăng trong một Lua script atomic.
 * DEL chỉ tốn thêm 1 query ở lần đọc kế tiếp nhưng KHÔNG BAO GIỜ lệch số.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostCounterServiceImpl implements PostCounterService {

    static Duration TTL = Duration.ofHours(6);
    static String FIELD_LIKE = "like";
    static String FIELD_COMMENT = "comment";
    static String FIELD_SHARE = "share";

    PostActionRepository postActionRepository;
    RedisTemplate<String, Object> redisTemplate;
    RedisCircuitBreaker circuitBreaker;

    /** Gán likeCount / commentCount / shareCount cho response. */
    @Override
    public void apply(PostResponse response, Long postId) {
        if (response == null || postId == null) {
            return;
        }
        String key = String.format(CacheNames.KEY_POST_COUNTS, postId);

        Map<Object, Object> cached = circuitBreaker.read("post.counts.get",
                () -> redisTemplate.opsForHash().entries(key), Map.of());

        if (cached != null && cached.size() == 3) {
            response.setLikeCount(parse(cached.get(FIELD_LIKE)));
            response.setCommentCount(parse(cached.get(FIELD_COMMENT)));
            response.setShareCount(parse(cached.get(FIELD_SHARE)));
            return;
        }

        Map<PostActionType, Long> counts = countFromDb(postId);
        long like = counts.getOrDefault(PostActionType.LIKE, 0L);
        long comment = counts.getOrDefault(PostActionType.COMMENT, 0L);
        long share = counts.getOrDefault(PostActionType.SHARE, 0L);

        response.setLikeCount(like);
        response.setCommentCount(comment);
        response.setShareCount(share);

        // Nạp ĐỦ cả 3 field, không bao giờ tăng dần từ trạng thái thiếu
        circuitBreaker.write("post.counts.set", () -> {
            redisTemplate.opsForHash().putAll(key, Map.of(
                    FIELD_LIKE, String.valueOf(like),
                    FIELD_COMMENT, String.valueOf(comment),
                    FIELD_SHARE, String.valueOf(share)));
            redisTemplate.expire(key, TTL);
        });
    }

    /** Xoá counter cache — gọi sau khi like/comment/share thay đổi. */
    @Override
    public void evict(Long postId) {
        if (postId == null) {
            return;
        }
        circuitBreaker.write("post.counts.evict",
                () -> redisTemplate.delete(String.format(CacheNames.KEY_POST_COUNTS, postId)));
    }

    /** Một query GROUP BY cho cả 3 loại action. */
    private Map<PostActionType, Long> countFromDb(Long postId) {
        Map<PostActionType, Long> result = new HashMap<>();
        List<Object[]> rows = postActionRepository.countActionsByPostId(postId);
        for (Object[] row : rows) {
            result.put((PostActionType) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }

    private long parse(Object value) {
        return value == null ? 0L : Long.parseLong(value.toString());
    }
}
