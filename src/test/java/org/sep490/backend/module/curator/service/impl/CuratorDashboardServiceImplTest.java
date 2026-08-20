package org.sep490.backend.module.curator.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.module.admin.dto.projection.DailyCountProjection;
import org.sep490.backend.module.admin.dto.projection.RouteEngagementProjection;
import org.sep490.backend.module.content.dto.projection.HotspotRatingSummaryProjection;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.sep490.backend.module.content.entity.enumeration.ReviewStatus;
import org.sep490.backend.module.content.entity.enumeration.RouteStatus;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.ReviewRepository;
import org.sep490.backend.module.content.repository.RouteRepository;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.curator.dto.projection.ContentStatusCountProjection;
import org.sep490.backend.module.curator.dto.projection.HotspotCheckInCountProjection;
import org.sep490.backend.module.curator.dto.projection.RatingSummaryProjection;
import org.sep490.backend.module.curator.dto.projection.RouteStatusCountProjection;
import org.sep490.backend.module.curator.dto.response.CuratorDashboardResponse;
import org.sep490.backend.module.exploration.entity.enumuration.RouteParticipantStatus;
import org.sep490.backend.module.exploration.repository.RouteParticipantRepository;
import org.sep490.backend.module.exploration.repository.UserHotspotProgressRepository;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit test cho dashboard curator. Clock cố định tại 2026-08-02 (Chủ nhật) giờ VN
 * để mọi mốc thời gian deterministic.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CuratorDashboardServiceImplTest {

    @Mock private HotspotRepository hotspotRepository;
    @Mock private RouteRepository routeRepository;
    @Mock private StoryRepository storyRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private UserHotspotProgressRepository userHotspotProgressRepository;
    @Mock private RouteParticipantRepository routeParticipantRepository;

    // 2026-08-02 12:00 giờ VN (Chủ nhật)
    @Spy private Clock clock = Clock.fixed(
            Instant.parse("2026-08-02T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @InjectMocks private CuratorDashboardServiceImpl service;

    // Implementation thuần (không Mockito) để gọi được cả bên trong thenReturn(...)
    // — dựng mock lồng trong stubbing đang mở sẽ gây UnfinishedStubbing.
    private static ContentStatusCountProjection contentCount(ContentStatus status, long total) {
        return new ContentStatusCountProjection() {
            @Override public ContentStatus getStatus() { return status; }
            @Override public Long getTotal() { return total; }
        };
    }

    private static RouteStatusCountProjection routeCount(RouteStatus status, long total) {
        return new RouteStatusCountProjection() {
            @Override public RouteStatus getStatus() { return status; }
            @Override public Long getTotal() { return total; }
        };
    }

    private static RatingSummaryProjection rating(Double average, Long total) {
        return new RatingSummaryProjection() {
            @Override public Double getAverageRating() { return average; }
            @Override public Long getTotalReviews() { return total; }
        };
    }

    private static DailyCountProjection daily(int year, int month, int day, long total) {
        return new DailyCountProjection() {
            @Override public Integer getBucketYear() { return year; }
            @Override public Integer getBucketMonth() { return month; }
            @Override public Integer getBucketDay() { return day; }
            @Override public Long getTotal() { return total; }
        };
    }

    private static RouteEngagementProjection route(Long id, String name, Long started, Long completed) {
        return new RouteEngagementProjection() {
            @Override public Long getRouteId() { return id; }
            @Override public String getRouteName() { return name; }
            @Override public Long getStartedCount() { return started; }
            @Override public Long getCompletedCount() { return completed; }
        };
    }

    private static HotspotCheckInCountProjection hotspotCheckIn(Long id, String name, long checkIns) {
        return new HotspotCheckInCountProjection() {
            @Override public Long getHotspotId() { return id; }
            @Override public String getHotspotName() { return name; }
            @Override public Long getCheckInCount() { return checkIns; }
        };
    }

    private static HotspotRatingSummaryProjection hotspotRating(Long id, Double average, Long total) {
        return new HotspotRatingSummaryProjection() {
            @Override public Long getHotspotId() { return id; }
            @Override public Double getAverageRating() { return average; }
            @Override public Long getTotalReviews() { return total; }
        };
    }

    // =====================================================================
    // Function: getDashboard - tổng quan nội dung
    // =====================================================================
    @Nested
    @DisplayName("contentSummary")
    class ContentSummaryTest {

        // UTCID01 - Normal: map status count đúng field, status thiếu fill 0
        @Test
        void contentSummary_sparseStatuses_zeroFilled() {
            when(hotspotRepository.countHotspotsByStatus(ContentStatus.DELETED))
                    .thenReturn(List.of(contentCount(ContentStatus.PUBLISHED, 18L)));
            when(routeRepository.countRoutesByStatus(RouteStatus.DELETED))
                    .thenReturn(List.of(routeCount(RouteStatus.PUBLISHED, 6L), routeCount(RouteStatus.DRAFT, 2L)));
            when(storyRepository.countStoriesByStatus(ContentStatus.DELETED))
                    .thenReturn(List.of(contentCount(ContentStatus.DRAFT, 9L)));

            CuratorDashboardResponse response = service.getDashboard();

            CuratorDashboardResponse.ContentSummary summary = response.getContentSummary();
            assertEquals(18L, summary.getPublishedHotspots());
            assertEquals(0L, summary.getDraftHotspots());
            assertEquals(6L, summary.getRouteCounts().getPublished());
            assertEquals(2L, summary.getRouteCounts().getDraft());
            assertEquals(0L, summary.getRouteCounts().getRecording());
            assertEquals(0L, summary.getRouteCounts().getPending());
            assertEquals(0L, summary.getPublishedStories());
            assertEquals(9L, summary.getDraftStories());
        }

        // UTCID02 - Abnormal: mọi repo rỗng → toàn 0, không NPE
        @Test
        void contentSummary_emptyRepos_allZerosNoNpe() {
            CuratorDashboardResponse response = service.getDashboard();

            CuratorDashboardResponse.ContentSummary summary = response.getContentSummary();
            assertEquals(0L, summary.getPublishedHotspots());
            assertEquals(0L, summary.getDraftHotspots());
            assertEquals(0L, summary.getRouteCounts().getPublished());
            assertEquals(0L, summary.getPublishedStories());
            assertEquals(0L, summary.getTotalCheckIns());
            assertEquals(0L, summary.getTotalReviews());
        }

        // UTCID03 - Abnormal: chưa có review ACTIVE → averageRating null, không ép về 0
        @Test
        void contentSummary_noReviews_averageRatingIsNull() {
            when(reviewRepository.summarizeGlobalRatings(ReviewStatus.ACTIVE))
                    .thenReturn(rating(null, 0L));

            CuratorDashboardResponse response = service.getDashboard();

            assertNull(response.getContentSummary().getAverageRating());
            assertEquals(0L, response.getContentSummary().getTotalReviews());
        }

        // UTCID04 - Abnormal: projection null → không NPE
        @Test
        void contentSummary_nullRatingProjection_noNpe() {
            when(reviewRepository.summarizeGlobalRatings(ReviewStatus.ACTIVE)).thenReturn(null);

            CuratorDashboardResponse response = service.getDashboard();

            assertNull(response.getContentSummary().getAverageRating());
            assertEquals(0L, response.getContentSummary().getTotalReviews());
        }

        // UTCID05 - Normal: averageRating làm tròn 1 chữ số (4.267 → 4.3)
        @Test
        void contentSummary_averageRating_roundedOneDecimal() {
            when(reviewRepository.summarizeGlobalRatings(ReviewStatus.ACTIVE))
                    .thenReturn(rating(4.267, 152L));

            CuratorDashboardResponse response = service.getDashboard();

            assertEquals(4.3, response.getContentSummary().getAverageRating());
            assertEquals(152L, response.getContentSummary().getTotalReviews());
        }

        // UTCID06 - Normal: totalCheckIns lấy từ count()
        @Test
        void contentSummary_totalCheckIns_fromCount() {
            when(userHotspotProgressRepository.count()).thenReturn(1234L);

            CuratorDashboardResponse response = service.getDashboard();

            assertEquals(1234L, response.getContentSummary().getTotalCheckIns());
        }
    }

    // =====================================================================
    // Function: getDashboard - biểu đồ check-in 7 ngày
    // =====================================================================
    @Nested
    @DisplayName("checkInTrend")
    class CheckInTrendTest {

        // UTCID01 - Normal: chỉ 2/7 ngày có dữ liệu, các ngày trống fill 0
        @Test
        void checkInTrend_sparseData_fillsSevenDaysWithZeros() {
            when(userHotspotProgressRepository.countCheckInsPerDay(any(), any()))
                    .thenReturn(List.of(daily(2026, 8, 2, 42L), daily(2026, 8, 1, 35L)));

            CuratorDashboardResponse response = service.getDashboard();

            List<CuratorDashboardResponse.CheckInPoint> daily = response.getCheckInTrend().getDaily();
            assertEquals(7, daily.size());
            assertEquals(42L, daily.get(6).getValue()); // hôm nay CN
            assertEquals(35L, daily.get(5).getValue()); // hôm qua T7
            for (int i = 0; i <= 4; i++) {
                assertEquals(0L, daily.get(i).getValue());
            }
        }

        // UTCID02 - Normal: nhãn thứ xoay đúng, phần tử cuối là "CN" (2026-08-02 là Chủ nhật)
        @Test
        void checkInTrend_dayLabels_rotateEndingToday() {
            CuratorDashboardResponse response = service.getDashboard();

            List<String> labels = response.getCheckInTrend().getDaily().stream()
                    .map(CuratorDashboardResponse.CheckInPoint::getDayLabel).toList();
            assertEquals(List.of("T2", "T3", "T4", "T5", "T6", "T7", "CN"), labels);
        }

        // UTCID03 - Normal: khoảng thời gian half-open [27/07 00:00, 03/08 00:00)
        @Test
        void checkInTrend_windowBoundaries_areHalfOpen() {
            service.getDashboard();

            ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(userHotspotProgressRepository).countCheckInsPerDay(from.capture(), to.capture());
            assertEquals(LocalDateTime.of(2026, 7, 27, 0, 0), from.getValue());
            assertEquals(LocalDateTime.of(2026, 8, 3, 0, 0), to.getValue());
        }

        // UTCID04 - Normal: today/yesterday lấy từ trend, một query duy nhất + percent 35 → 42 = +20.0
        @Test
        void checkInTrend_countsDeriveFromTrendSingleQuery() {
            when(userHotspotProgressRepository.countCheckInsPerDay(any(), any()))
                    .thenReturn(List.of(daily(2026, 8, 2, 42L), daily(2026, 8, 1, 35L)));

            CuratorDashboardResponse response = service.getDashboard();

            assertEquals(42L, response.getCheckInTrend().getCheckInsToday());
            assertEquals(35L, response.getCheckInTrend().getCheckInsYesterday());
            assertEquals(20.0, response.getCheckInTrend().getChangePercent());
            verify(userHotspotProgressRepository, times(1)).countCheckInsPerDay(any(), any());
        }

        // UTCID05 - Abnormal: hôm qua = 0 thì percent là null, không chia 0
        @Test
        void checkInTrend_zeroYesterday_percentIsNull() {
            when(userHotspotProgressRepository.countCheckInsPerDay(any(), any()))
                    .thenReturn(List.of(daily(2026, 8, 2, 42L)));

            CuratorDashboardResponse response = service.getDashboard();

            assertNull(response.getCheckInTrend().getChangePercent());
        }
    }

    // =====================================================================
    // Function: getDashboard - top tuyến
    // =====================================================================
    @Nested
    @DisplayName("topRoutes")
    class TopRoutesTest {

        // UTCID01 - Normal: map đủ field + completionRate làm tròn 1 chữ số
        @Test
        void topRoutes_mapsProjection() {
            when(routeParticipantRepository.findTopRouteEngagement(any(), any(), any()))
                    .thenReturn(List.of(route(7L, "Bến Nhà Rồng", 340L, 120L)));

            CuratorDashboardResponse response = service.getDashboard();

            CuratorDashboardResponse.RouteEngagementPoint point =
                    response.getTopContent().getTopRoutes().get(0);
            assertEquals(7L, point.getRouteId());
            assertEquals("Bến Nhà Rồng", point.getRouteName());
            assertEquals(340L, point.getStartedCount());
            assertEquals(120L, point.getCompletedCount());
            assertEquals(35.3, point.getCompletionRate());
        }

        // UTCID02 - Normal: một call duy nhất với PageRequest(0,5) không Sort
        @Test
        void topRoutes_singleCallWithUnsortedPageRequest() {
            service.getDashboard();

            verify(routeParticipantRepository, times(1)).findTopRouteEngagement(
                    eq(RouteParticipantStatus.COMPLETED), eq(RouteStatus.DELETED), eq(PageRequest.of(0, 5)));
        }

        // UTCID03 - Abnormal: started = 0 → completionRate null
        @Test
        void topRoutes_zeroStarted_rateIsNull() {
            when(routeParticipantRepository.findTopRouteEngagement(any(), any(), any()))
                    .thenReturn(List.of(route(9L, "Tuyến mới", 0L, 0L)));

            CuratorDashboardResponse response = service.getDashboard();

            assertNull(response.getTopContent().getTopRoutes().get(0).getCompletionRate());
        }

        // UTCID04 - Abnormal: repo rỗng → list rỗng, không null
        @Test
        void topRoutes_empty_returnsEmptyList() {
            CuratorDashboardResponse response = service.getDashboard();

            assertNotNull(response.getTopContent().getTopRoutes());
            assertTrue(response.getTopContent().getTopRoutes().isEmpty());
        }
    }

    // =====================================================================
    // Function: getDashboard - top hotspot
    // =====================================================================
    @Nested
    @DisplayName("topHotspots")
    class TopHotspotsTest {

        // UTCID01 - Normal: join rating theo hotspotId, làm tròn 1 chữ số
        @Test
        void topHotspots_joinsRatingSummaries() {
            when(userHotspotProgressRepository.findTopHotspotCheckIns(any(), any()))
                    .thenReturn(List.of(
                            hotspotCheckIn(3L, "Dinh Độc Lập", 210L),
                            hotspotCheckIn(8L, "Chợ Bến Thành", 150L)));
            when(reviewRepository.summarizeRatingsByHotspotIds(anyList(), any()))
                    .thenReturn(List.of(hotspotRating(3L, 4.267, 96L)));

            CuratorDashboardResponse response = service.getDashboard();

            List<CuratorDashboardResponse.TopHotspotPoint> points =
                    response.getTopContent().getTopHotspots();
            assertEquals(2, points.size());
            assertEquals(3L, points.get(0).getHotspotId());
            assertEquals("Dinh Độc Lập", points.get(0).getHotspotName());
            assertEquals(210L, points.get(0).getCheckInCount());
            assertEquals(4.3, points.get(0).getAverageRating());
            assertEquals(96L, points.get(0).getTotalReviews());
            // hotspot không có review → null / 0
            assertNull(points.get(1).getAverageRating());
            assertEquals(0L, points.get(1).getTotalReviews());
        }

        // UTCID02 - Normal: gọi batch rating đúng 1 lần với đúng ids + ACTIVE
        @Test
        void topHotspots_batchRatingSingleCall() {
            when(userHotspotProgressRepository.findTopHotspotCheckIns(any(), any()))
                    .thenReturn(List.of(hotspotCheckIn(3L, "Dinh Độc Lập", 210L)));

            service.getDashboard();

            verify(reviewRepository, times(1))
                    .summarizeRatingsByHotspotIds(eq(List.of(3L)), eq(ReviewStatus.ACTIVE));
        }

        // UTCID03 - Abnormal: top list rỗng → không gọi batch rating (tránh IN rỗng)
        @Test
        void topHotspots_emptyList_skipsRatingQuery() {
            CuratorDashboardResponse response = service.getDashboard();

            assertTrue(response.getTopContent().getTopHotspots().isEmpty());
            verify(reviewRepository, never()).summarizeRatingsByHotspotIds(anyList(), any());
        }

        // UTCID04 - Normal: query top với PageRequest(0,5) và loại DELETED
        @Test
        void topHotspots_pageRequestAndExcludedStatus() {
            service.getDashboard();

            verify(userHotspotProgressRepository, times(1)).findTopHotspotCheckIns(
                    eq(ContentStatus.DELETED), eq(PageRequest.of(0, 5)));
        }
    }

    // =====================================================================
    // Function: getDashboard - ngân sách query
    // =====================================================================
    @Nested
    @DisplayName("queryBudget")
    class QueryBudgetTest {

        // UTCID01 - Normal: đúng 8 lời gọi repo khi top hotspot rỗng — chốt budget chống regression
        @Test
        void getDashboard_exactlyEightRepositoryCallsWhenNoTopHotspots() {
            service.getDashboard();

            verify(hotspotRepository).countHotspotsByStatus(ContentStatus.DELETED);
            verify(routeRepository).countRoutesByStatus(RouteStatus.DELETED);
            verify(storyRepository).countStoriesByStatus(ContentStatus.DELETED);
            verify(userHotspotProgressRepository).count();
            verify(reviewRepository).summarizeGlobalRatings(ReviewStatus.ACTIVE);
            verify(userHotspotProgressRepository).countCheckInsPerDay(any(), any());
            verify(routeParticipantRepository).findTopRouteEngagement(any(), any(), any());
            verify(userHotspotProgressRepository).findTopHotspotCheckIns(any(), any());

            verifyNoMoreInteractions(hotspotRepository, routeRepository, storyRepository,
                    reviewRepository, userHotspotProgressRepository, routeParticipantRepository);
        }

        // UTCID02 - Normal: có top hotspot → thêm đúng 1 call batch rating, tổng 9
        @Test
        void getDashboard_nineCallsWithTopHotspots() {
            when(userHotspotProgressRepository.findTopHotspotCheckIns(any(), any()))
                    .thenReturn(List.of(hotspotCheckIn(3L, "Dinh Độc Lập", 210L)));

            service.getDashboard();

            verify(hotspotRepository).countHotspotsByStatus(ContentStatus.DELETED);
            verify(routeRepository).countRoutesByStatus(RouteStatus.DELETED);
            verify(storyRepository).countStoriesByStatus(ContentStatus.DELETED);
            verify(userHotspotProgressRepository).count();
            verify(reviewRepository).summarizeGlobalRatings(ReviewStatus.ACTIVE);
            verify(userHotspotProgressRepository).countCheckInsPerDay(any(), any());
            verify(routeParticipantRepository).findTopRouteEngagement(any(), any(), any());
            verify(userHotspotProgressRepository).findTopHotspotCheckIns(any(), any());
            verify(reviewRepository).summarizeRatingsByHotspotIds(anyList(), any());

            verifyNoMoreInteractions(hotspotRepository, routeRepository, storyRepository,
                    reviewRepository, userHotspotProgressRepository, routeParticipantRepository);
        }
    }
}
