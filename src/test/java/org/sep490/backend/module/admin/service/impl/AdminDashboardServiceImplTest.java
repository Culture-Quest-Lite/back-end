package org.sep490.backend.module.admin.service.impl;

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
import org.sep490.backend.module.admin.dto.projection.HotspotPublishSummaryProjection;
import org.sep490.backend.module.admin.dto.projection.MonthlyCountProjection;
import org.sep490.backend.module.admin.dto.projection.RevenueSummaryProjection;
import org.sep490.backend.module.admin.dto.projection.RouteEngagementProjection;
import org.sep490.backend.module.admin.dto.projection.UserSummaryProjection;
import org.sep490.backend.module.admin.dto.response.AdminDashboardResponse;
import org.sep490.backend.module.admin.entity.enumeration.InvoicePaymentStatus;
import org.sep490.backend.module.admin.entity.enumeration.InvoiceStatus;
import org.sep490.backend.module.admin.repository.InvoiceRepository;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.sep490.backend.module.content.entity.enumeration.RouteStatus;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.exploration.entity.enumuration.ProgressStatus;
import org.sep490.backend.module.exploration.repository.RouteParticipantRepository;
import org.sep490.backend.module.exploration.repository.UserHotspotProgressRepository;
import org.sep490.backend.module.social.entity.enumeration.PostStatus;
import org.sep490.backend.module.social.repository.PostRepository;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit test cho dashboard admin. Clock cố định tại 2026-08-02 (Chủ nhật) giờ VN
 * để mọi mốc thời gian deterministic.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminDashboardServiceImplTest {

    @Mock private UserHotspotProgressRepository userHotspotProgressRepository;
    @Mock private UserRepository userRepository;
    @Mock private HotspotRepository hotspotRepository;
    @Mock private PostRepository postRepository;
    @Mock private RouteParticipantRepository routeParticipantRepository;
    @Mock private InvoiceRepository invoiceRepository;

    // 2026-08-02 12:00 giờ VN (Chủ nhật)
    @Spy private Clock clock = Clock.fixed(
            Instant.parse("2026-08-02T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @InjectMocks private AdminDashboardServiceImpl service;

    // Implementation thuần (không Mockito) để gọi được cả bên trong thenReturn(...)
    // — dựng mock lồng trong stubbing đang mở sẽ gây UnfinishedStubbing.
    private static DailyCountProjection daily(int year, int month, int day, long total) {
        return new DailyCountProjection() {
            @Override public Integer getBucketYear() { return year; }
            @Override public Integer getBucketMonth() { return month; }
            @Override public Integer getBucketDay() { return day; }
            @Override public Long getTotal() { return total; }
        };
    }

    private static MonthlyCountProjection monthly(int year, int month, long total) {
        return new MonthlyCountProjection() {
            @Override public Integer getBucketYear() { return year; }
            @Override public Integer getBucketMonth() { return month; }
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

    private static RevenueSummaryProjection revenue(long total, long partner, long premium,
                                                    long thisMonth, long lastMonth, long paidInvoices,
                                                    long activePartner, long activePremium) {
        return new RevenueSummaryProjection() {
            @Override public Long getTotalRevenue() { return total; }
            @Override public Long getPartnerRevenue() { return partner; }
            @Override public Long getPremiumRevenue() { return premium; }
            @Override public Long getRevenueThisMonth() { return thisMonth; }
            @Override public Long getRevenueLastMonth() { return lastMonth; }
            @Override public Long getPaidInvoices() { return paidInvoices; }
            @Override public Long getActivePartnerSubscriptions() { return activePartner; }
            @Override public Long getActivePremiumSubscriptions() { return activePremium; }
        };
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

            AdminDashboardResponse response = service.getDashboard();

            assertEquals(7, response.getCheckInTrend().size());
            assertEquals(42L, response.getCheckInTrend().get(6).getValue()); // hôm nay CN
            assertEquals(35L, response.getCheckInTrend().get(5).getValue()); // hôm qua T7
            for (int i = 0; i <= 4; i++) {
                assertEquals(0L, response.getCheckInTrend().get(i).getValue());
            }
        }

        // UTCID02 - Normal: nhãn thứ xoay đúng, phần tử cuối là "CN" (2026-08-02 là Chủ nhật)
        @Test
        void checkInTrend_dayLabels_rotateEndingToday() {
            AdminDashboardResponse response = service.getDashboard();

            List<String> labels = response.getCheckInTrend().stream()
                    .map(AdminDashboardResponse.CheckInPoint::getDayLabel).toList();
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

        // UTCID04 - Abnormal: repo trả rỗng, vẫn đủ 7 phần tử toàn 0
        @Test
        void checkInTrend_emptyData_returnsSevenZeros() {
            AdminDashboardResponse response = service.getDashboard();

            assertEquals(7, response.getCheckInTrend().size());
            assertTrue(response.getCheckInTrend().stream().allMatch(p -> p.getValue() == 0L));
        }
    }

    // =====================================================================
    // Function: getDashboard - tăng trưởng người dùng 12 tháng
    // =====================================================================
    @Nested
    @DisplayName("userGrowth")
    class UserGrowthTest {

        // UTCID01 - Normal: 3 tháng có dữ liệu, đủ 12 phần tử với gaps = 0
        @Test
        void userGrowth_sparseData_fillsTwelveMonths() {
            when(userRepository.countNewUsersPerMonth(any(), any()))
                    .thenReturn(List.of(monthly(2025, 9, 40L), monthly(2026, 1, 55L), monthly(2026, 8, 87L)));

            AdminDashboardResponse response = service.getDashboard();

            assertEquals(12, response.getUserGrowth().size());
            assertEquals(40L, response.getUserGrowth().get(0).getNewUsers());   // Thg 9/2025 — tháng đầu cửa sổ
            assertEquals(87L, response.getUserGrowth().get(11).getNewUsers());  // Thg 8/2026
            assertEquals(9, response.getUserGrowth().stream().filter(p -> p.getNewUsers() == 0L).count());
        }

        // UTCID02 - Normal: nhãn tháng span 2025→2026, bắt đầu Thg 9/2025 kết thúc Thg 8/2026
        @Test
        void userGrowth_monthLabels_spanTwoYears() {
            AdminDashboardResponse response = service.getDashboard();

            assertEquals("Thg 9", response.getUserGrowth().get(0).getMonthLabel());
            assertEquals(2025, response.getUserGrowth().get(0).getYear());
            assertEquals("Thg 8", response.getUserGrowth().get(11).getMonthLabel());
            assertEquals(2026, response.getUserGrowth().get(11).getYear());
        }

        // UTCID03 - Normal: mốc from/to đúng [01/09/2025, 01/09/2026)
        @Test
        void userGrowth_windowBoundaries() {
            service.getDashboard();

            ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(userRepository).countNewUsersPerMonth(from.capture(), to.capture());
            assertEquals(LocalDateTime.of(2025, 9, 1, 0, 0), from.getValue());
            assertEquals(LocalDateTime.of(2026, 9, 1, 0, 0), to.getValue());
        }
    }

    // =====================================================================
    // Function: getDashboard - summary
    // =====================================================================
    @Nested
    @DisplayName("summary")
    class SummaryTest {

        // UTCID01 - Normal: checkInsToday/Yesterday lấy từ trend, không query thêm
        @Test
        void summary_checkInCounts_deriveFromTrendSingleQuery() {
            when(userHotspotProgressRepository.countCheckInsPerDay(any(), any()))
                    .thenReturn(List.of(daily(2026, 8, 2, 42L), daily(2026, 8, 1, 35L)));

            AdminDashboardResponse response = service.getDashboard();

            assertEquals(42L, response.getSummary().getCheckInsToday());
            assertEquals(35L, response.getSummary().getCheckInsYesterday());
            verify(userHotspotProgressRepository, times(1)).countCheckInsPerDay(any(), any());
        }

        // UTCID02 - Normal: phần trăm thay đổi 35 → 42 = +20.0
        @Test
        void summary_changePercent_computed() {
            when(userHotspotProgressRepository.countCheckInsPerDay(any(), any()))
                    .thenReturn(List.of(daily(2026, 8, 2, 42L), daily(2026, 8, 1, 35L)));

            AdminDashboardResponse response = service.getDashboard();

            assertEquals(20.0, response.getSummary().getCheckInsChangePercent());
        }

        // UTCID03 - Abnormal: hôm qua = 0 thì percent là null, không chia 0
        @Test
        void summary_zeroYesterday_percentIsNull() {
            when(userHotspotProgressRepository.countCheckInsPerDay(any(), any()))
                    .thenReturn(List.of(daily(2026, 8, 2, 42L)));

            AdminDashboardResponse response = service.getDashboard();

            assertNull(response.getSummary().getCheckInsChangePercent());
        }

        // UTCID04 - Abnormal: mọi projection null → toàn 0, không NPE
        @Test
        void summary_nullProjections_defaultsToZero() {
            when(userRepository.summarizeUsers(any(), any())).thenReturn(null);
            when(hotspotRepository.summarizePublishedHotspots(any(), any())).thenReturn(null);
            when(invoiceRepository.summarizeRevenue(any(), any(), any(), any(), any())).thenReturn(null);

            AdminDashboardResponse response = service.getDashboard();

            assertEquals(0L, response.getSummary().getTotalUsers());
            assertEquals(0L, response.getSummary().getPublishedHotspots());
            assertEquals(0L, response.getRevenue().getTotalRevenue());
        }

        // UTCID05 - Normal: weekStart là thứ Hai 27/07 00:00
        @Test
        void summary_weekStart_isMonday() {
            service.getDashboard();

            ArgumentCaptor<LocalDateTime> weekStart = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(hotspotRepository).summarizePublishedHotspots(eq(ContentStatus.PUBLISHED), weekStart.capture());
            assertEquals(LocalDateTime.of(2026, 7, 27, 0, 0), weekStart.getValue());
        }

        // UTCID06 - Normal: pendingPosts đếm PENDING từ repo
        @Test
        void summary_pendingPosts_fromCountByStatus() {
            when(postRepository.countByStatus(PostStatus.PENDING)).thenReturn(23L);

            AdminDashboardResponse response = service.getDashboard();

            assertEquals(23L, response.getSummary().getPendingPosts());
        }
    }

    // =====================================================================
    // Function: getDashboard - tương tác tuyến
    // =====================================================================
    @Nested
    @DisplayName("routeEngagement")
    class RouteEngagementTest {

        // UTCID01 - Normal: map đủ field + completionRate làm tròn 1 chữ số
        @Test
        void routeEngagement_mapsProjection() {
            when(routeParticipantRepository.findTopRouteEngagement(any(), any(), any()))
                    .thenReturn(List.of(route(7L, "Bến Nhà Rồng", 340L, 120L)));

            AdminDashboardResponse response = service.getDashboard();

            AdminDashboardResponse.RouteEngagementPoint point = response.getRouteEngagement().get(0);
            assertEquals(7L, point.getRouteId());
            assertEquals("Bến Nhà Rồng", point.getRouteName());
            assertEquals(340L, point.getStartedCount());
            assertEquals(120L, point.getCompletedCount());
            assertEquals(35.3, point.getCompletionRate());
        }

        // UTCID02 - Normal: một call duy nhất với PageRequest(0,5) không Sort — chốt chống N+1
        @Test
        void routeEngagement_singleCallWithUnsortedPageRequest() {
            service.getDashboard();

            verify(routeParticipantRepository, times(1)).findTopRouteEngagement(
                    eq(ProgressStatus.COMPLETED), eq(RouteStatus.DELETED), eq(PageRequest.of(0, 5)));
        }

        // UTCID03 - Abnormal: repo rỗng → list rỗng, không null
        @Test
        void routeEngagement_empty_returnsEmptyList() {
            AdminDashboardResponse response = service.getDashboard();

            assertNotNull(response.getRouteEngagement());
            assertTrue(response.getRouteEngagement().isEmpty());
        }

        // UTCID04 - Abnormal: started = 0 → completionRate null
        @Test
        void routeEngagement_zeroStarted_rateIsNull() {
            when(routeParticipantRepository.findTopRouteEngagement(any(), any(), any()))
                    .thenReturn(List.of(route(9L, "Tuyến mới", 0L, 0L)));

            AdminDashboardResponse response = service.getDashboard();

            assertNull(response.getRouteEngagement().get(0).getCompletionRate());
        }
    }

    // =====================================================================
    // Function: getDashboard - doanh thu
    // =====================================================================
    @Nested
    @DisplayName("revenue")
    class RevenueTest {

        // UTCID01 - Normal: projection map 1:1 + phần trăm 4tr → 6tr = +50.0
        @Test
        void revenue_mapsProjectionAndComputesPercent() {
            when(invoiceRepository.summarizeRevenue(any(), any(), any(), any(), any()))
                    .thenReturn(revenue(45_000_000L, 40_000_000L, 5_000_000L,
                            6_000_000L, 4_000_000L, 31L, 12L, 44L));

            AdminDashboardResponse response = service.getDashboard();

            AdminDashboardResponse.RevenueSummary rev = response.getRevenue();
            assertEquals(45_000_000L, rev.getTotalRevenue());
            assertEquals(40_000_000L, rev.getPartnerRevenue());
            assertEquals(5_000_000L, rev.getPremiumRevenue());
            assertEquals(50.0, rev.getRevenueChangePercent());
            assertEquals(31L, rev.getPaidInvoices());
            assertEquals(12L, rev.getActivePartnerSubscriptions());
            assertEquals(44L, rev.getActivePremiumSubscriptions());
        }

        // UTCID02 - Abnormal: tháng trước = 0 → percent null
        @Test
        void revenue_zeroLastMonth_percentIsNull() {
            when(invoiceRepository.summarizeRevenue(any(), any(), any(), any(), any()))
                    .thenReturn(revenue(6_000_000L, 6_000_000L, 0L, 6_000_000L, 0L, 3L, 2L, 0L));

            AdminDashboardResponse response = service.getDashboard();

            assertNull(response.getRevenue().getRevenueChangePercent());
        }

        // UTCID03 - Normal: mốc tháng + enum đúng
        @Test
        void revenue_monthBoundariesAndEnums() {
            service.getDashboard();

            ArgumentCaptor<LocalDateTime> monthStart = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> prevMonthStart = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(invoiceRepository).summarizeRevenue(
                    eq(InvoicePaymentStatus.PAID), eq(InvoiceStatus.ACTIVE),
                    monthStart.capture(), prevMonthStart.capture(), any());
            assertEquals(LocalDateTime.of(2026, 8, 1, 0, 0), monthStart.getValue());
            assertEquals(LocalDateTime.of(2026, 7, 1, 0, 0), prevMonthStart.getValue());
        }
    }

    // =====================================================================
    // Function: getDashboard - ngân sách query
    // =====================================================================
    @Nested
    @DisplayName("queryBudget")
    class QueryBudgetTest {

        // UTCID01 - Normal: đúng 7 lời gọi repo, không hơn — chốt budget chống regression
        @Test
        void getDashboard_exactlySevenRepositoryCalls() {
            service.getDashboard();

            verify(userHotspotProgressRepository).countCheckInsPerDay(any(), any());
            verify(userRepository).countNewUsersPerMonth(any(), any());
            verify(userRepository).summarizeUsers(eq(UserStatus.ACTIVE), any());
            verify(hotspotRepository).summarizePublishedHotspots(eq(ContentStatus.PUBLISHED), any());
            verify(postRepository).countByStatus(PostStatus.PENDING);
            verify(routeParticipantRepository).findTopRouteEngagement(any(), any(), any());
            verify(invoiceRepository).summarizeRevenue(any(), any(), any(), any(), any());

            verifyNoMoreInteractions(userHotspotProgressRepository, userRepository,
                    hotspotRepository, postRepository, routeParticipantRepository, invoiceRepository);
        }
    }
}
