package org.sep490.backend.module.curator.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.config.redis.CacheNames;
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
import org.sep490.backend.module.curator.dto.response.CuratorDashboardResponse.CheckInPoint;
import org.sep490.backend.module.curator.dto.response.CuratorDashboardResponse.CheckInTrend;
import org.sep490.backend.module.curator.dto.response.CuratorDashboardResponse.ContentSummary;
import org.sep490.backend.module.curator.dto.response.CuratorDashboardResponse.RouteEngagementPoint;
import org.sep490.backend.module.curator.dto.response.CuratorDashboardResponse.RouteStatusCounts;
import org.sep490.backend.module.curator.dto.response.CuratorDashboardResponse.TopContent;
import org.sep490.backend.module.curator.dto.response.CuratorDashboardResponse.TopHotspotPoint;
import org.sep490.backend.module.curator.service.CuratorDashboardService;
import org.sep490.backend.module.exploration.entity.enumuration.ProgressStatus;
import org.sep490.backend.module.exploration.repository.RouteParticipantRepository;
import org.sep490.backend.module.exploration.repository.UserHotspotProgressRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CuratorDashboardServiceImpl implements CuratorDashboardService {

    static final int CHECK_IN_DAYS = 7;
    static final int TOP_LIMIT = 5;

    static final Map<DayOfWeek, String> VN_DAY_LABELS = new EnumMap<>(Map.of(
            DayOfWeek.MONDAY, "T2", DayOfWeek.TUESDAY, "T3", DayOfWeek.WEDNESDAY, "T4",
            DayOfWeek.THURSDAY, "T5", DayOfWeek.FRIDAY, "T6", DayOfWeek.SATURDAY, "T7",
            DayOfWeek.SUNDAY, "CN"));

    HotspotRepository hotspotRepository;
    RouteRepository routeRepository;
    StoryRepository storyRepository;
    ReviewRepository reviewRepository;
    UserHotspotProgressRepository userHotspotProgressRepository;
    RouteParticipantRepository routeParticipantRepository;
    Clock clock;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.CURATOR_DASHBOARD, key = "'current'", unless = "#result == null")
    public CuratorDashboardResponse getDashboard() {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime dayFrom = today.minusDays(CHECK_IN_DAYS - 1L).atStartOfDay();
        LocalDateTime dayTo = today.plusDays(1).atStartOfDay();

        List<ContentStatusCountProjection> hotspotCounts =
                hotspotRepository.countHotspotsByStatus(ContentStatus.DELETED);
        List<RouteStatusCountProjection> routeCounts =
                routeRepository.countRoutesByStatus(RouteStatus.DELETED);
        List<ContentStatusCountProjection> storyCounts =
                storyRepository.countStoriesByStatus(ContentStatus.DELETED);
        long totalCheckIns = userHotspotProgressRepository.count();
        RatingSummaryProjection ratingSummary = reviewRepository.summarizeGlobalRatings(ReviewStatus.ACTIVE);
        List<DailyCountProjection> dailyRows = userHotspotProgressRepository.countCheckInsPerDay(dayFrom, dayTo);
        List<RouteEngagementProjection> routeRows = routeParticipantRepository.findTopRouteEngagement(
                ProgressStatus.COMPLETED, RouteStatus.DELETED, PageRequest.of(0, TOP_LIMIT));
        List<HotspotCheckInCountProjection> hotspotRows = userHotspotProgressRepository.findTopHotspotCheckIns(
                ContentStatus.DELETED, PageRequest.of(0, TOP_LIMIT));

        List<CheckInPoint> daily = buildCheckInTrend(today, dailyRows);
        long checkInsToday = daily.get(daily.size() - 1).getValue();
        long checkInsYesterday = daily.get(daily.size() - 2).getValue();

        return CuratorDashboardResponse.builder()
                .contentSummary(buildContentSummary(hotspotCounts, routeCounts, storyCounts,
                        totalCheckIns, ratingSummary))
                .checkInTrend(CheckInTrend.builder()
                        .checkInsToday(checkInsToday)
                        .checkInsYesterday(checkInsYesterday)
                        .changePercent(changePercent(checkInsToday, checkInsYesterday))
                        .daily(daily)
                        .build())
                .topContent(TopContent.builder()
                        .topRoutes(buildTopRoutes(routeRows))
                        .topHotspots(buildTopHotspots(hotspotRows))
                        .build())
                .build();
    }

    private ContentSummary buildContentSummary(List<ContentStatusCountProjection> hotspotCounts,
                                               List<RouteStatusCountProjection> routeCounts,
                                               List<ContentStatusCountProjection> storyCounts,
                                               long totalCheckIns,
                                               RatingSummaryProjection ratingSummary) {
        Map<ContentStatus, Long> hotspotByStatus = toContentStatusMap(hotspotCounts);
        Map<ContentStatus, Long> storyByStatus = toContentStatusMap(storyCounts);
        Map<RouteStatus, Long> routeByStatus = routeCounts.stream().collect(Collectors.toMap(
                RouteStatusCountProjection::getStatus, r -> nz(r.getTotal()),
                Long::sum, () -> new EnumMap<>(RouteStatus.class)));

        Double averageRating = ratingSummary != null ? ratingSummary.getAverageRating() : null;
        return ContentSummary.builder()
                .publishedHotspots(hotspotByStatus.getOrDefault(ContentStatus.PUBLISHED, 0L))
                .draftHotspots(hotspotByStatus.getOrDefault(ContentStatus.DRAFT, 0L))
                .routeCounts(RouteStatusCounts.builder()
                        .draft(routeByStatus.getOrDefault(RouteStatus.DRAFT, 0L))
                        .recording(routeByStatus.getOrDefault(RouteStatus.RECORDING, 0L))
                        .onHold(routeByStatus.getOrDefault(RouteStatus.ON_HOLD, 0L))
                        .trial(routeByStatus.getOrDefault(RouteStatus.TRIAL, 0L))
                        .pending(routeByStatus.getOrDefault(RouteStatus.PENDING, 0L))
                        .published(routeByStatus.getOrDefault(RouteStatus.PUBLISHED, 0L))
                        .build())
                .publishedStories(storyByStatus.getOrDefault(ContentStatus.PUBLISHED, 0L))
                .draftStories(storyByStatus.getOrDefault(ContentStatus.DRAFT, 0L))
                .totalCheckIns(totalCheckIns)
                .averageRating(round1(averageRating))
                .totalReviews(nz(ratingSummary != null ? ratingSummary.getTotalReviews() : null))
                .build();
    }

    private Map<ContentStatus, Long> toContentStatusMap(List<ContentStatusCountProjection> rows) {
        return rows.stream().collect(Collectors.toMap(
                ContentStatusCountProjection::getStatus, r -> nz(r.getTotal()),
                Long::sum, () -> new EnumMap<>(ContentStatus.class)));
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

    private List<RouteEngagementPoint> buildTopRoutes(List<RouteEngagementProjection> rows) {
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

    private List<TopHotspotPoint> buildTopHotspots(List<HotspotCheckInCountProjection> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> hotspotIds = rows.stream().map(HotspotCheckInCountProjection::getHotspotId).toList();
        Map<Long, HotspotRatingSummaryProjection> ratingById = reviewRepository
                .summarizeRatingsByHotspotIds(hotspotIds, ReviewStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(HotspotRatingSummaryProjection::getHotspotId, Function.identity()));
        return rows.stream().map(r -> {
            HotspotRatingSummaryProjection rating = ratingById.get(r.getHotspotId());
            return TopHotspotPoint.builder()
                    .hotspotId(r.getHotspotId())
                    .hotspotName(r.getHotspotName())
                    .checkInCount(nz(r.getCheckInCount()))
                    .averageRating(round1(rating != null ? rating.getAverageRating() : null))
                    .totalReviews(nz(rating != null ? rating.getTotalReviews() : null))
                    .build();
        }).toList();
    }

    private Double changePercent(long current, long previous) {
        if (previous == 0L) {
            return null;
        }
        return Math.round(((double) (current - previous) / previous) * 1000d) / 10d;
    }

    private Double round1(Double value) {
        return value == null ? null : Math.round(value * 10d) / 10d;
    }

    private long nz(Long value) {
        return value != null ? value : 0L;
    }
}
