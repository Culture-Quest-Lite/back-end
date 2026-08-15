package org.sep490.backend.module.content.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.config.redis.RedisCircuitBreaker;
import org.sep490.backend.module.content.dto.projection.HotspotRatingSummaryProjection;
import org.sep490.backend.module.content.dto.projection.TargetRatingSummaryProjection;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.content.dto.response.RouteResponse;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.entity.enumeration.ReviewStatus;
import org.sep490.backend.module.content.entity.enumeration.ReviewTargetType;
import org.sep490.backend.module.content.repository.ReviewRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho TỔNG HỢP ĐÁNH GIÁ (điểm trung bình + số lượt) có cache Redis theo từng đối tượng.
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RatingSummaryServiceImplTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private RedisCircuitBreaker circuitBreaker;

    @InjectMocks private RatingSummaryServiceImpl ratingSummaryService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(circuitBreaker.read(anyString(), any(), any())).thenAnswer(inv -> {
            Supplier<?> supplier = inv.getArgument(1);
            return supplier.get();
        });
        doAnswer(inv -> {
            Runnable runnable = inv.getArgument(1);
            runnable.run();
            return null;
        }).when(circuitBreaker).write(anyString(), any());
    }

    private static HotspotResponse hotspot(Long id) {
        HotspotResponse response = new HotspotResponse();
        response.setHotspotId(id);
        return response;
    }

    private static RouteResponse route(Long id) {
        RouteResponse response = new RouteResponse();
        response.setRouteId(id);
        return response;
    }

    private static StoryResponse story(Long id) {
        StoryResponse response = new StoryResponse();
        response.setStoryId(id);
        return response;
    }

    private static HotspotRatingSummaryProjection hotspotProjection(Long id, Double avg, Long total) {
        return new HotspotRatingSummaryProjection() {
            @Override public Long getHotspotId() { return id; }
            @Override public Double getAverageRating() { return avg; }
            @Override public Long getTotalReviews() { return total; }
        };
    }

    private static TargetRatingSummaryProjection targetProjection(Long id, Double avg, Long total) {
        return new TargetRatingSummaryProjection() {
            @Override public Long getTargetId() { return id; }
            @Override public Double getAverageRating() { return avg; }
            @Override public Long getTotalReviews() { return total; }
        };
    }

    // =====================================================================
    // Function: applyToHotspots
    // =====================================================================
    @Nested
    @DisplayName("applyToHotspots")
    class ApplyToHotspotsTest {

        // UTCID01 - Boundary: danh sách null -> thoát sớm, không gọi DB
        @Test
        void applyToHotspots_nullList_returnsWithoutQuery() {
            assertDoesNotThrow(() -> ratingSummaryService.applyToHotspots(null));

            verifyNoInteractions(reviewRepository);
        }

        // UTCID02 - Boundary: danh sách rỗng -> thoát sớm
        @Test
        void applyToHotspots_emptyList_returnsWithoutQuery() {
            ratingSummaryService.applyToHotspots(new ArrayList<>());

            verifyNoInteractions(reviewRepository);
        }

        // UTCID03 - Normal: cache miss -> truy vấn DB và gán điểm 4.5 / 12 lượt
        @Test
        void applyToHotspots_cacheMiss_loadsFromDatabase() {
            when(valueOps.multiGet(anyList())).thenReturn(Arrays.asList((Object) null));
            when(reviewRepository.summarizeRatingsByHotspotIds(List.of(1L), ReviewStatus.ACTIVE))
                    .thenReturn(List.of(hotspotProjection(1L, 4.5, 12L)));

            List<HotspotResponse> responses = new ArrayList<>(List.of(hotspot(1L)));
            ratingSummaryService.applyToHotspots(responses);

            assertEquals(4.5, responses.get(0).getAverageRating());
            assertEquals(12L, responses.get(0).getTotalReviews());
        }

        // UTCID04 - Normal: cache hit -> lấy từ Redis, KHÔNG truy vấn DB
        @Test
        void applyToHotspots_cacheHit_skipsDatabase() {
            when(valueOps.multiGet(anyList()))
                    .thenReturn(List.of(new RatingSummaryServiceImpl.Summary(3.8, 5L)));

            List<HotspotResponse> responses = new ArrayList<>(List.of(hotspot(1L)));
            ratingSummaryService.applyToHotspots(responses);

            assertEquals(3.8, responses.get(0).getAverageRating());
            assertEquals(5L, responses.get(0).getTotalReviews());
            verify(reviewRepository, never()).summarizeRatingsByHotspotIds(anyList(), any());
        }

        // UTCID05 - Boundary: cache hit một phần -> chỉ truy vấn DB cho id còn thiếu
        @Test
        void applyToHotspots_partialCacheHit_queriesOnlyMissingIds() {
            when(valueOps.multiGet(anyList())).thenReturn(Arrays.asList(
                    new RatingSummaryServiceImpl.Summary(3.8, 5L), null));
            when(reviewRepository.summarizeRatingsByHotspotIds(List.of(2L), ReviewStatus.ACTIVE))
                    .thenReturn(List.of(hotspotProjection(2L, 5.0, 3L)));

            List<HotspotResponse> responses = new ArrayList<>(List.of(hotspot(1L), hotspot(2L)));
            ratingSummaryService.applyToHotspots(responses);

            assertEquals(3.8, responses.get(0).getAverageRating());
            assertEquals(5.0, responses.get(1).getAverageRating());
            verify(reviewRepository).summarizeRatingsByHotspotIds(List.of(2L), ReviewStatus.ACTIVE);
        }

        // UTCID06 - Boundary: địa điểm chưa có đánh giá nào -> hiển thị 0.0 và 0 lượt
        @Test
        void applyToHotspots_noReviews_returnsZeroDefaults() {
            when(valueOps.multiGet(anyList())).thenReturn(Arrays.asList((Object) null));
            when(reviewRepository.summarizeRatingsByHotspotIds(anyList(), any())).thenReturn(List.of());

            List<HotspotResponse> responses = new ArrayList<>(List.of(hotspot(1L)));
            ratingSummaryService.applyToHotspots(responses);

            assertEquals(0.0, responses.get(0).getAverageRating());
            assertEquals(0L, responses.get(0).getTotalReviews());
        }

        // UTCID07 - Normal: id trùng lặp trong danh sách -> chỉ truy vấn 1 lần, gán cho cả 2
        @Test
        void applyToHotspots_duplicateIds_queriedOnceAndAppliedToAll() {
            when(valueOps.multiGet(anyList())).thenReturn(Arrays.asList((Object) null));
            when(reviewRepository.summarizeRatingsByHotspotIds(List.of(1L), ReviewStatus.ACTIVE))
                    .thenReturn(List.of(hotspotProjection(1L, 4.0, 8L)));

            List<HotspotResponse> responses = new ArrayList<>(List.of(hotspot(1L), hotspot(1L)));
            ratingSummaryService.applyToHotspots(responses);

            assertEquals(4.0, responses.get(0).getAverageRating());
            assertEquals(4.0, responses.get(1).getAverageRating());
            verify(reviewRepository, times(1)).summarizeRatingsByHotspotIds(anyList(), any());
        }

        // UTCID08 - Normal: ghi kết quả ngược lại Redis để lần sau khỏi truy vấn DB
        @Test
        void applyToHotspots_cacheMiss_writesResultBackToRedis() {
            when(valueOps.multiGet(anyList())).thenReturn(Arrays.asList((Object) null));
            when(reviewRepository.summarizeRatingsByHotspotIds(anyList(), any()))
                    .thenReturn(List.of(hotspotProjection(1L, 4.5, 12L)));

            ratingSummaryService.applyToHotspots(new ArrayList<>(List.of(hotspot(1L))));

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(valueOps).multiSet(captor.capture());
            assertTrue(captor.getValue().containsKey("rating:hotspot:1"));
        }

        // UTCID09 - Normal: applyToHotspot cho 1 đối tượng -> trả về chính object đã gán điểm
        @Test
        void applyToHotspot_singleResponse_returnsSameObject() {
            when(valueOps.multiGet(anyList()))
                    .thenReturn(List.of(new RatingSummaryServiceImpl.Summary(4.2, 7L)));

            HotspotResponse response = hotspot(1L);
            HotspotResponse result = ratingSummaryService.applyToHotspot(response);

            assertSame(response, result);
            assertEquals(4.2, result.getAverageRating());
        }
    }

    // =====================================================================
    // Function: applyToRoutes / applyToStories
    // =====================================================================
    @Nested
    @DisplayName("applyToRoutes")
    class ApplyToRoutesTest {

        // UTCID01 - Normal: tuyến đường lấy điểm đánh giá từ DB, key cache riêng theo loại "route"
        @Test
        void applyToRoutes_cacheMiss_loadsAndCachesWithRouteKey() {
            when(valueOps.multiGet(anyList())).thenReturn(Arrays.asList((Object) null));
            when(reviewRepository.summarizeRatingsByRouteIds(List.of(10L), ReviewStatus.ACTIVE))
                    .thenReturn(List.of(targetProjection(10L, 4.7, 30L)));

            List<RouteResponse> responses = new ArrayList<>(List.of(route(10L)));
            ratingSummaryService.applyToRoutes(responses);

            assertEquals(4.7, responses.get(0).getAverageRating());
            assertEquals(30L, responses.get(0).getTotalReviews());

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(valueOps).multiSet(captor.capture());
            assertTrue(captor.getValue().containsKey("rating:route:10"));
        }

        // UTCID02 - Normal: câu chuyện lấy điểm đánh giá, key cache riêng theo loại "story"
        @Test
        void applyToStories_cacheMiss_loadsAndCachesWithStoryKey() {
            when(valueOps.multiGet(anyList())).thenReturn(Arrays.asList((Object) null));
            when(reviewRepository.summarizeRatingsByStoryIds(List.of(20L), ReviewStatus.ACTIVE))
                    .thenReturn(List.of(targetProjection(20L, 3.0, 2L)));

            List<StoryResponse> responses = new ArrayList<>(List.of(story(20L)));
            ratingSummaryService.applyToStories(responses);

            assertEquals(3.0, responses.get(0).getAverageRating());

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(valueOps).multiSet(captor.capture());
            assertTrue(captor.getValue().containsKey("rating:story:20"));
        }

        // UTCID03 - Boundary: tuyến chưa có đánh giá -> 0.0 / 0 lượt, không null
        @Test
        void applyToRoutes_noReviews_returnsZeroDefaults() {
            when(valueOps.multiGet(anyList())).thenReturn(Arrays.asList((Object) null));
            when(reviewRepository.summarizeRatingsByRouteIds(anyList(), any())).thenReturn(List.of());

            List<RouteResponse> responses = new ArrayList<>(List.of(route(10L)));
            ratingSummaryService.applyToRoutes(responses);

            assertEquals(0.0, responses.get(0).getAverageRating());
            assertEquals(0L, responses.get(0).getTotalReviews());
        }

        // UTCID04 - Abnormal: Redis chết -> vẫn lấy được điểm từ DB, không ném lỗi
        @Test
        void applyToRoutes_redisDown_stillLoadsFromDatabase() {
            doReturn(null).when(circuitBreaker).read(anyString(), any(), any());
            when(reviewRepository.summarizeRatingsByRouteIds(anyList(), any()))
                    .thenReturn(List.of(targetProjection(10L, 4.1, 9L)));

            List<RouteResponse> responses = new ArrayList<>(List.of(route(10L)));
            assertDoesNotThrow(() -> ratingSummaryService.applyToRoutes(responses));

            assertEquals(4.1, responses.get(0).getAverageRating());
        }
    }

    // =====================================================================
    // Function: evict
    // =====================================================================
    @Nested
    @DisplayName("evict")
    class EvictTest {

        // UTCID01 - Abnormal: targetType null -> bỏ qua, không gọi Redis
        @Test
        void evict_nullTargetType_doesNothing() {
            ratingSummaryService.evict(null, 1L);

            verifyNoInteractions(circuitBreaker);
        }

        // UTCID02 - Abnormal: targetId null -> bỏ qua, không gọi Redis
        @Test
        void evict_nullTargetId_doesNothing() {
            ratingSummaryService.evict(ReviewTargetType.HOTSPOT, null);

            verifyNoInteractions(circuitBreaker);
        }

        // UTCID03 - Normal: xóa cache đánh giá của địa điểm sau khi có review mới
        @Test
        void evict_hotspot_deletesHotspotKey() {
            ratingSummaryService.evict(ReviewTargetType.HOTSPOT, 1L);

            verify(redisTemplate).delete("rating:hotspot:1");
        }

        // UTCID04 - Normal: xóa cache đánh giá của tuyến đường
        @Test
        void evict_route_deletesRouteKey() {
            ratingSummaryService.evict(ReviewTargetType.ROUTE, 10L);

            verify(redisTemplate).delete("rating:route:10");
        }
    }

    // =====================================================================
    // Function: Summary.average / Summary.total
    // =====================================================================
    @Nested
    @DisplayName("Summary.average")
    class SummaryTest {

        // UTCID01 - Boundary: chưa có đánh giá (null) -> trả 0.0 thay vì null
        @Test
        void average_nullRawAverage_returnsZero() {
            assertEquals(0.0, new RatingSummaryServiceImpl.Summary(null, null).average());
        }

        // UTCID02 - Normal: làm tròn 1 chữ số thập phân (4.36 -> 4.4)
        @Test
        void average_roundsToOneDecimal() {
            assertEquals(4.4, new RatingSummaryServiceImpl.Summary(4.36, 10L).average());
        }

        // UTCID03 - Boundary: làm tròn xuống (4.24 -> 4.2)
        @Test
        void average_roundsDownWhenBelowHalf() {
            assertEquals(4.2, new RatingSummaryServiceImpl.Summary(4.244, 10L).average());
        }

        // UTCID04 - Boundary: điểm tối đa 5.0 giữ nguyên
        @Test
        void average_maxRating_staysFive() {
            assertEquals(5.0, new RatingSummaryServiceImpl.Summary(5.0, 100L).average());
        }

        // UTCID05 - Boundary: tổng số lượt null -> trả 0
        @Test
        void total_nullRawTotal_returnsZero() {
            assertEquals(0L, new RatingSummaryServiceImpl.Summary(null, null).total());
        }
    }
}
