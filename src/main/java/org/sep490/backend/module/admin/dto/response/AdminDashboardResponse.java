package org.sep490.backend.module.admin.dto.response;

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
public class AdminDashboardResponse {

    Summary summary;

    List<CheckInPoint> checkInTrend;

    List<UserGrowthPoint> userGrowth;

    List<RouteEngagementPoint> routeEngagement;

    RevenueSummary revenue;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Summary {
        long checkInsToday;
        long checkInsYesterday;
        Double checkInsChangePercent;
        long publishedHotspots;
        long publishedHotspotsThisWeek;
        long totalUsers;
        long activeUsers;
        long newUsersThisMonth;
        long pendingPosts;
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
    public static class UserGrowthPoint {
        @JsonProperty("m")
        String monthLabel;
        @JsonProperty("u")
        long newUsers;
        int year;
        int month;
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
    public static class RevenueSummary {
        long totalRevenue;
        long partnerRevenue;
        long premiumRevenue;
        long revenueThisMonth;
        long revenueLastMonth;
        Double revenueChangePercent;
        long paidInvoices;
        long activePartnerSubscriptions;
        long activePremiumSubscriptions;
    }
}
