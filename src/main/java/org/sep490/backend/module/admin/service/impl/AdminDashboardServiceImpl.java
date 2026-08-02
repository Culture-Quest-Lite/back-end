package org.sep490.backend.module.admin.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.admin.dto.projection.DailyCountProjection;
import org.sep490.backend.module.admin.dto.projection.HotspotPublishSummaryProjection;
import org.sep490.backend.module.admin.dto.projection.MonthlyCountProjection;
import org.sep490.backend.module.admin.dto.projection.RevenueSummaryProjection;
import org.sep490.backend.module.admin.dto.projection.RouteEngagementProjection;
import org.sep490.backend.module.admin.dto.projection.UserSummaryProjection;
import org.sep490.backend.module.admin.dto.response.AdminDashboardResponse;
import org.sep490.backend.module.admin.dto.response.AdminDashboardResponse.CheckInPoint;
import org.sep490.backend.module.admin.dto.response.AdminDashboardResponse.RevenueSummary;
import org.sep490.backend.module.admin.dto.response.AdminDashboardResponse.RouteEngagementPoint;
import org.sep490.backend.module.admin.dto.response.AdminDashboardResponse.Summary;
import org.sep490.backend.module.admin.dto.response.AdminDashboardResponse.UserGrowthPoint;
import org.sep490.backend.module.admin.entity.enumeration.InvoicePaymentStatus;
import org.sep490.backend.module.admin.entity.enumeration.InvoiceStatus;
import org.sep490.backend.module.admin.repository.InvoiceRepository;
import org.sep490.backend.module.admin.service.AdminDashboardService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminDashboardServiceImpl implements AdminDashboardService {

    static final int CHECK_IN_DAYS = 7;
    static final int GROWTH_MONTHS = 12;
    static final int TOP_ROUTE_LIMIT = 5;

    static final Map<DayOfWeek, String> VN_DAY_LABELS = new EnumMap<>(Map.of(
            DayOfWeek.MONDAY, "T2", DayOfWeek.TUESDAY, "T3", DayOfWeek.WEDNESDAY, "T4",
            DayOfWeek.THURSDAY, "T5", DayOfWeek.FRIDAY, "T6", DayOfWeek.SATURDAY, "T7",
            DayOfWeek.SUNDAY, "CN"));

    UserHotspotProgressRepository userHotspotProgressRepository;
    UserRepository userRepository;
    HotspotRepository hotspotRepository;
    PostRepository postRepository;
    RouteParticipantRepository routeParticipantRepository;
    InvoiceRepository invoiceRepository;
    Clock clock;

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        LocalDate today = LocalDate.now(clock);
        YearMonth currentMonth = YearMonth.from(today);

        LocalDateTime dayFrom = today.minusDays(CHECK_IN_DAYS - 1L).atStartOfDay();
        LocalDateTime dayTo = today.plusDays(1).atStartOfDay();
        LocalDateTime monthFrom = currentMonth.minusMonths(GROWTH_MONTHS - 1L).atDay(1).atStartOfDay();
        LocalDateTime monthTo = currentMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime prevMonthStart = currentMonth.minusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now(clock);

        List<DailyCountProjection> dailyRows = userHotspotProgressRepository.countCheckInsPerDay(dayFrom, dayTo);
        List<MonthlyCountProjection> monthlyRows = userRepository.countNewUsersPerMonth(monthFrom, monthTo);
        UserSummaryProjection userSummary = userRepository.summarizeUsers(UserStatus.ACTIVE, monthStart);
        HotspotPublishSummaryProjection hotspotSummary =
                hotspotRepository.summarizePublishedHotspots(ContentStatus.PUBLISHED, weekStart);
        long pendingPosts = postRepository.countByStatus(PostStatus.PENDING);
        List<RouteEngagementProjection> routeRows = routeParticipantRepository.findTopRouteEngagement(
                ProgressStatus.COMPLETED, RouteStatus.DELETED, PageRequest.of(0, TOP_ROUTE_LIMIT));
        RevenueSummaryProjection revenue =
                invoiceRepository.summarizeRevenue(InvoicePaymentStatus.PAID, InvoiceStatus.ACTIVE,
                        monthStart, prevMonthStart, now);

        List<CheckInPoint> checkInTrend = buildCheckInTrend(today, dailyRows);
        long checkInsToday = checkInTrend.get(checkInTrend.size() - 1).getValue();
        long checkInsYesterday = checkInTrend.get(checkInTrend.size() - 2).getValue();

        return AdminDashboardResponse.builder()
                .summary(Summary.builder()
                        .checkInsToday(checkInsToday)
                        .checkInsYesterday(checkInsYesterday)
                        .checkInsChangePercent(changePercent(checkInsToday, checkInsYesterday))
                        .publishedHotspots(nz(hotspotSummary != null ? hotspotSummary.getTotalPublished() : null))
                        .publishedHotspotsThisWeek(nz(hotspotSummary != null ? hotspotSummary.getPublishedThisWeek() : null))
                        .totalUsers(nz(userSummary != null ? userSummary.getTotalUsers() : null))
                        .activeUsers(nz(userSummary != null ? userSummary.getActiveUsers() : null))
                        .newUsersThisMonth(nz(userSummary != null ? userSummary.getNewUsersThisMonth() : null))
                        .pendingPosts(pendingPosts)
                        .build())
                .checkInTrend(checkInTrend)
                .userGrowth(buildUserGrowth(currentMonth, monthlyRows))
                .routeEngagement(buildRouteEngagement(routeRows))
                .revenue(buildRevenueSummary(revenue))
                .build();
    }

    private List<CheckInPoint> buildCheckInTrend(LocalDate today, List<DailyCountProjection> rows) {
        Map<LocalDate, Long> byDate = rows.stream().collect(Collectors.toMap(
                r -> LocalDate.of(r.getBucketYear(), r.getBucketMonth(), r.getBucketDay()),
                r -> nz(r.getTotal())));
        List<CheckInPoint> points = new ArrayList<>(CHECK_IN_DAYS);
        for (int i = CHECK_IN_DAYS - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            points.add(CheckInPoint.builder()
                    .dayLabel(VN_DAY_LABELS.get(date.getDayOfWeek()))
                    .value(byDate.getOrDefault(date, 0L))
                    .date(date)
                    .build());
        }
        return points;
    }

    private List<UserGrowthPoint> buildUserGrowth(YearMonth current, List<MonthlyCountProjection> rows) {
        Map<YearMonth, Long> byMonth = rows.stream().collect(Collectors.toMap(
                r -> YearMonth.of(r.getBucketYear(), r.getBucketMonth()),
                r -> nz(r.getTotal())));
        List<UserGrowthPoint> points = new ArrayList<>(GROWTH_MONTHS);
        for (int i = GROWTH_MONTHS - 1; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            points.add(UserGrowthPoint.builder()
                    .monthLabel("Thg " + ym.getMonthValue())
                    .newUsers(byMonth.getOrDefault(ym, 0L))
                    .year(ym.getYear())
                    .month(ym.getMonthValue())
                    .build());
        }
        return points;
    }

    private List<RouteEngagementPoint> buildRouteEngagement(List<RouteEngagementProjection> rows) {
        return rows.stream().map(r -> {
            long started = nz(r.getStartedCount());
            long completed = nz(r.getCompletedCount());
            return RouteEngagementPoint.builder()
                    .routeId(r.getRouteId())
                    .routeName(r.getRouteName())
                    .startedCount(started)
                    .completedCount(completed)
                    .completionRate(started == 0 ? null
                            : Math.round((double) completed / started * 1000d) / 10d)
                    .build();
        }).toList();
    }

    private RevenueSummary buildRevenueSummary(RevenueSummaryProjection p) {
        Function<Function<RevenueSummaryProjection, Long>, Long> get =
                getter -> p == null ? 0L : nz(getter.apply(p));
        long thisMonth = get.apply(RevenueSummaryProjection::getRevenueThisMonth);
        long lastMonth = get.apply(RevenueSummaryProjection::getRevenueLastMonth);
        return RevenueSummary.builder()
                .totalRevenue(get.apply(RevenueSummaryProjection::getTotalRevenue))
                .partnerRevenue(get.apply(RevenueSummaryProjection::getPartnerRevenue))
                .premiumRevenue(get.apply(RevenueSummaryProjection::getPremiumRevenue))
                .revenueThisMonth(thisMonth)
                .revenueLastMonth(lastMonth)
                .revenueChangePercent(changePercent(thisMonth, lastMonth))
                .paidInvoices(get.apply(RevenueSummaryProjection::getPaidInvoices))
                .activePartnerSubscriptions(get.apply(RevenueSummaryProjection::getActivePartnerSubscriptions))
                .activePremiumSubscriptions(get.apply(RevenueSummaryProjection::getActivePremiumSubscriptions))
                .build();
    }

    private Double changePercent(long current, long previous) {
        if (previous == 0L) {
            return null;
        }
        return Math.round(((double) (current - previous) / previous) * 1000d) / 10d;
    }

    private long nz(Long value) {
        return value != null ? value : 0L;
    }
}
