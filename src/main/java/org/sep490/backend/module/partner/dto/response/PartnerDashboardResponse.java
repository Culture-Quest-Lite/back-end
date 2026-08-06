package org.sep490.backend.module.partner.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sep490.backend.module.admin.entity.enumeration.BillingCycleEnum;
import org.sep490.backend.module.admin.entity.enumeration.InvoicePaymentStatus;
import org.sep490.backend.module.admin.entity.enumeration.InvoiceStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PartnerDashboardResponse {

    SubscriptionInfo subscriptionInfo;

    List<UsageItem> usageItems;

    VoucherSummary voucherSummary;

    List<RedemptionPoint> redemptionTrend;

    List<TopVoucher> topVouchers;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SubscriptionInfo {
        String planName;
        InvoiceStatus status;
        InvoicePaymentStatus paymentStatus;
        BillingCycleEnum billingCycle;
        LocalDateTime startDate;
        LocalDateTime endDate;
        Long paidAmount;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UsageItem {
        String usageKey;
        Integer currentUsage;
        Integer maxAllowed;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class VoucherSummary {
        long totalVouchers;
        long activeVouchers;
        long expiredVouchers;
        long outOfStockVouchers;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RedemptionPoint {
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
    public static class TopVoucher {
        Long voucherId;
        String voucherName;
        String voucherCode;
        long redemptionCount;
    }
}
