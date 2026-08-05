package org.sep490.backend.module.social.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.config.redis.RedisCircuitBreaker;
import org.sep490.backend.module.social.dto.response.PostResponse;
import org.sep490.backend.module.social.entity.enumeration.PostActionType;
import org.sep490.backend.module.social.repository.PostActionRepository;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm chứng logic cache-aside của counter bài viết.
 * Trọng tâm: cache miss phải đếm lại ĐỦ từ DB, không bao giờ tăng dần từ trạng thái thiếu.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PostCounterService - đếm like/comment/share")
class PostCounterServiceImplTest {

    @Mock
    private PostActionRepository postActionRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private RedisCircuitBreaker circuitBreaker;

    @InjectMocks
    private PostCounterServiceImpl postCounterService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        // circuitBreaker.read(...) chạy thẳng supplier
        when(circuitBreaker.read(anyString(), any(), any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        });

        // circuitBreaker.write(...) chạy thẳng runnable
        org.mockito.Mockito.doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(circuitBreaker).write(anyString(), any());
    }

    @Nested
    @DisplayName("Khi cache có đủ dữ liệu")
    class CacheHit {

        @Test
        @DisplayName("Đọc từ Redis, KHÔNG truy vấn DB")
        void docTuCacheKhongGoiDb() {
            when(hashOperations.entries("post:5:counts")).thenReturn(Map.of(
                    "like", "10", "comment", "3", "share", "2"));

            PostResponse response = new PostResponse();
            postCounterService.apply(response, 5L);

            assertThat(response.getLikeCount()).isEqualTo(10L);
            assertThat(response.getCommentCount()).isEqualTo(3L);
            assertThat(response.getShareCount()).isEqualTo(2L);
            verify(postActionRepository, never()).countActionsByPostId(any());
        }
    }

    @Nested
    @DisplayName("Khi cache trống")
    class CacheMiss {

        @Test
        @DisplayName("Đếm lại từ DB và nạp ĐỦ cả 3 field vào Redis")
        void demLaiTuDbVaNapDuBaField() {
            when(hashOperations.entries("post:7:counts")).thenReturn(Map.of());
            when(postActionRepository.countActionsByPostId(7L)).thenReturn(List.of(
                    new Object[]{PostActionType.LIKE, 4L},
                    new Object[]{PostActionType.COMMENT, 1L}));

            PostResponse response = new PostResponse();
            postCounterService.apply(response, 7L);

            assertThat(response.getLikeCount()).isEqualTo(4L);
            assertThat(response.getCommentCount()).isEqualTo(1L);
            // Loại không có row trong DB phải là 0, không phải null
            assertThat(response.getShareCount()).isZero();

            // Phải ghi đủ 3 field, tránh counter bắt đầu từ trạng thái thiếu
            verify(hashOperations).putAll("post:7:counts", Map.of(
                    "like", "4", "comment", "1", "share", "0"));
        }

        @Test
        @DisplayName("Post chưa có tương tác nào thì trả 0 cho cả 3")
        void postChuaCoTuongTac() {
            when(hashOperations.entries("post:9:counts")).thenReturn(Map.of());
            when(postActionRepository.countActionsByPostId(9L)).thenReturn(List.of());

            PostResponse response = new PostResponse();
            postCounterService.apply(response, 9L);

            assertThat(response.getLikeCount()).isZero();
            assertThat(response.getCommentCount()).isZero();
            assertThat(response.getShareCount()).isZero();
        }
    }

    @Nested
    @DisplayName("Khi cache thiếu field")
    class CachePartial {

        @Test
        @DisplayName("Chỉ có 2/3 field thì coi như miss, đếm lại từ DB")
        void thieuFieldThiDemLai() {
            // Trạng thái hỏng: thiếu field share
            when(hashOperations.entries("post:11:counts")).thenReturn(Map.of(
                    "like", "99", "comment", "5"));
            when(postActionRepository.countActionsByPostId(11L)).thenReturn(
                    List.<Object[]>of(new Object[]{PostActionType.LIKE, 2L}));

            PostResponse response = new PostResponse();
            postCounterService.apply(response, 11L);

            // Phải lấy số THẬT từ DB, không dùng giá trị hỏng trong cache
            assertThat(response.getLikeCount()).isEqualTo(2L);
            assertThat(response.getShareCount()).isZero();
        }
    }

    @Nested
    @DisplayName("Tham số không hợp lệ")
    class InvalidInput {

        @Test
        @DisplayName("postId null thì bỏ qua, không gọi DB")
        void postIdNull() {
            PostResponse response = new PostResponse();
            postCounterService.apply(response, null);
            verify(postActionRepository, never()).countActionsByPostId(any());
        }

        @Test
        @DisplayName("evict với postId null thì bỏ qua")
        void evictNull() {
            postCounterService.evict(null);
            verify(redisTemplate, never()).delete(anyString());
        }
    }
}
