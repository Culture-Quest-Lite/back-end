package org.sep490.backend.module.partner.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.module.admin.dto.projection.DailyCountProjection;
import org.sep490.backend.module.admin.entity.Invoice;
import org.sep490.backend.module.admin.entity.SubscriptionPlan;
import org.sep490.backend.module.admin.entity.SubscriptionUsage;
import org.sep490.backend.module.admin.entity.enumeration.BillingCycleEnum;
import org.sep490.backend.module.admin.entity.enumeration.InvoicePaymentStatus;
import org.sep490.backend.module.admin.entity.enumeration.InvoiceStatus;
import org.sep490.backend.module.admin.repository.InvoiceRepository;
import org.sep490.backend.module.admin.repository.SubscriptionUsageRepository;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.partner.dto.projection.TopVoucherProjection;
import org.sep490.backend.module.partner.dto.projection.VoucherSummaryProjection;
import org.sep490.backend.module.partner.dto.response.PartnerDashboardResponse;
import org.sep490.backend.module.partner.repository.VoucherRepository;
import org.sep490.backend.module.partner.repository.VoucherUsageRepository;
import org.sep490.backend.module.user.service.UserService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartnerDashboardServiceImplTest {

    @Mock private UserService userService;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private SubscriptionUsageRepository subscriptionUsageRepository;
    @Mock private VoucherRepository voucherRepository;
    @Mock private VoucherUsageRepository voucherUsageRepository;

    @Spy private Clock clock = Clock.fixed(
            Instant.parse("2026-08-02T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @InjectMocks private PartnerDashboardServiceImpl service;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder().userId(10L).username("partner_shop").build();
        when(userService.getCurrentUser()).thenReturn(mockUser);
    }

    private static VoucherSummaryProjection voucherSummary(long total, long active, long expired, long outOfStock) {
        return new VoucherSummaryProjection() {
            @Override public Long getTotalVouchers() { return total; }
            @Override public Long getActiveVouchers() { return active; }
            @Override public Long getExpiredVouchers() { return expired; }
            @Override public Long getOutOfStockVouchers() { return outOfStock; }
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

    private static TopVoucherProjection topVoucher(Long id, String name, String code, Long count) {
        return new TopVoucherProjection() {
            @Override public Long getVoucherId() { return id; }
            @Override public String getVoucherName() { return name; }
            @Override public String getVoucherCode() { return code; }
            @Override public Long getRedemptionCount() { return count; }
        };
    }

    @Nested
    @DisplayName("Subscription & Usages")
    class SubscriptionTest {

        @Test
        void getDashboard_withActiveSubscription_returnsSubscriptionInfoAndUsages() {
            SubscriptionPlan plan = SubscriptionPlan.builder()
                    .subscriptionPlanId(1L)
                    .subscriptionPlanName("Gói Khởi Nghiệp")
                    .build();

            Invoice invoice = Invoice.builder()
                    .invoiceId(100L)
                    .subscriptionPlan(plan)
                    .status(InvoiceStatus.ACTIVE)
                    .paymentStatus(InvoicePaymentStatus.PAID)
                    .billingCycle(BillingCycleEnum.MONTHLY)
                    .startDate(LocalDateTime.of(2026, 8, 1, 0, 0))
                    .endDate(LocalDateTime.of(2026, 9, 1, 0, 0))
                    .paidAmount(500000L)
                    .build();

            SubscriptionUsage usage1 = SubscriptionUsage.builder()
                    .subscriptionUsageId(1L)
                    .usageKey("MAX_VOUCHER")
                    .currentUsage(5)
                    .maxAllowed(20)
                    .build();

            when(invoiceRepository.findPartnerInvoicesForUser(10L))
                    .thenReturn(List.of(invoice));
            when(subscriptionUsageRepository.findByInvoice_InvoiceId(100L))
                    .thenReturn(List.of(usage1));

            PartnerDashboardResponse response = service.getDashboard();

            assertNotNull(response.getSubscriptionInfo());
            assertEquals("Gói Khởi Nghiệp", response.getSubscriptionInfo().getPlanName());
            assertEquals(InvoiceStatus.ACTIVE, response.getSubscriptionInfo().getStatus());
            assertEquals(500000L, response.getSubscriptionInfo().getPaidAmount());

            assertEquals(1, response.getUsageItems().size());
            assertEquals("MAX_VOUCHER", response.getUsageItems().get(0).getUsageKey());
            assertEquals(5, response.getUsageItems().get(0).getCurrentUsage());
            assertEquals(20, response.getUsageItems().get(0).getMaxAllowed());
        }

        @Test
        void getDashboard_noSubscription_returnsNullInfoAndEmptyUsages() {
            when(invoiceRepository.findPartnerInvoicesForUser(10L))
                    .thenReturn(List.of());

            PartnerDashboardResponse response = service.getDashboard();

            assertNull(response.getSubscriptionInfo());
            assertTrue(response.getUsageItems().isEmpty());
        }
    }

    @Nested
    @DisplayName("Voucher Summary & Trends")
    class VoucherTest {

        @Test
        void getDashboard_voucherSummaryAndRedemptionTrend() {
            when(voucherRepository.summarizeVouchersByPartner(10L))
                    .thenReturn(voucherSummary(15L, 10L, 3L, 2L));

            when(voucherUsageRepository.countRedemptionsPerDay(eq(10L), any(), any()))
                    .thenReturn(List.of(daily(2026, 8, 2, 12L), daily(2026, 8, 1, 8L)));

            when(voucherUsageRepository.findTopRedeemedVouchers(eq(10L), any()))
                    .thenReturn(List.of(topVoucher(1L, "Giảm 20%", "SALE20", 25L)));

            PartnerDashboardResponse response = service.getDashboard();

            // Summary
            assertEquals(15L, response.getVoucherSummary().getTotalVouchers());
            assertEquals(10L, response.getVoucherSummary().getActiveVouchers());
            assertEquals(3L, response.getVoucherSummary().getExpiredVouchers());
            assertEquals(2L, response.getVoucherSummary().getOutOfStockVouchers());

            // Trend - 7 days filled
            assertEquals(7, response.getRedemptionTrend().size());
            assertEquals(12L, response.getRedemptionTrend().get(6).getValue()); // today (Sunday 02/08)
            assertEquals(8L, response.getRedemptionTrend().get(5).getValue());  // yesterday (Saturday 01/08)
            assertEquals(0L, response.getRedemptionTrend().get(0).getValue());

            // Top Vouchers
            assertEquals(1, response.getTopVouchers().size());
            assertEquals("SALE20", response.getTopVouchers().get(0).getVoucherCode());
            assertEquals(25L, response.getTopVouchers().get(0).getRedemptionCount());
        }

        @Test
        void getDashboard_nullProjections_handlesWithoutException() {
            when(voucherRepository.summarizeVouchersByPartner(10L)).thenReturn(null);
            when(voucherUsageRepository.countRedemptionsPerDay(eq(10L), any(), any())).thenReturn(List.of());
            when(voucherUsageRepository.findTopRedeemedVouchers(eq(10L), any())).thenReturn(List.of());

            PartnerDashboardResponse response = service.getDashboard();

            assertNotNull(response.getVoucherSummary());
            assertEquals(0L, response.getVoucherSummary().getTotalVouchers());
            assertEquals(7, response.getRedemptionTrend().size());
            assertTrue(response.getTopVouchers().isEmpty());
        }
    }
}
