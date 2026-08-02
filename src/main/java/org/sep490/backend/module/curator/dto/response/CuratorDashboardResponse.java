package org.sep490.backend.module.curator.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CuratorDashboardResponse {

    ContentSummary contentSummary;

    CheckInTrend checkInTrend;

    TopContent topContent;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ContentSummary {
        long publishedHotspots;
        long draftHotspots;
        RouteStatusCounts routeCounts;
        long publishedStories;
        long draftStories;
        long totalCheckIns;
        Double averageRating;
        long totalReviews;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RouteStatusCounts {
        long draft;
        long recording;
        long onHold;
        long trial;
        long pending;
        long published;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CheckInTrend {
        long checkInsToday;
        long checkInsYesterday;
        Double changePercent;
        List<CheckInPoint> daily;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CheckInPoint {
        @JsonProperty("d")
        String dayLabel;
        @JsonProperty("v")
        long value;
        LocalDate date;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TopContent {
        List<RouteEngagementPoint> topRoutes;
        List<TopHotspotPoint> topHotspots;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RouteEngagementPoint {
        Long routeId;
        @JsonProperty("r")
        String routeName;
        @JsonProperty("views")
        long startedCount;
        @JsonProperty("completes")
        long completedCount;
        Double completionRate;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TopHotspotPoint {
        Long hotspotId;
        @JsonProperty("h")
        String hotspotName;
        @JsonProperty("checkIns")
        long checkInCount;
        Double averageRating;
        long totalReviews;
    }
}
