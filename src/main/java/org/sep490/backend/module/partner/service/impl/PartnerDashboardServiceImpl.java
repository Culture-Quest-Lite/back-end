package org.sep490.backend.module.partner.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.config.redis.CacheNames;
import org.sep490.backend.module.admin.dto.projection.DailyCountProjection;
import org.sep490.backend.module.admin.entity.Invoice;
import org.sep490.backend.module.admin.entity.SubscriptionUsage;
import org.sep490.backend.module.admin.entity.enumeration.InvoiceStatus;
import org.sep490.backend.module.admin.repository.InvoiceRepository;
import org.sep490.backend.module.admin.repository.SubscriptionUsageRepository;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.partner.dto.projection.TopVoucherProjection;
import org.sep490.backend.module.partner.dto.projection.VoucherSummaryProjection;
import org.sep490.backend.module.partner.dto.response.PartnerDashboardResponse;
import org.sep490.backend.module.partner.dto.response.PartnerDashboardResponse.*;
import org.sep490.backend.module.partner.repository.VoucherRepository;
import org.sep490.backend.module.partner.repository.VoucherUsageRepository;
import org.sep490.backend.module.partner.service.PartnerDashboardService;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PartnerDashboardServiceImpl implements PartnerDashboardService {

    static final int REDEMPTION_DAYS = 7;
    static final int TOP_VOUCHER_LIMIT = 5;

    static final Map<DayOfWeek, String> VN_DAY_LABELS = new EnumMap<>(Map.of(
            DayOfWeek.MONDAY, "T2", DayOfWeek.TUESDAY, "T3", DayOfWeek.WEDNESDAY, "T4",
            DayOfWeek.THURSDAY, "T5", DayOfWeek.FRIDAY, "T6", DayOfWeek.SATURDAY, "T7",
            DayOfWeek.SUNDAY, "CN"));

    UserService userService;
    InvoiceRepository invoiceRepository;
    SubscriptionUsageRepository subscriptionUsageRepository;
    VoucherRepository voucherRepository;
    VoucherUsageRepository voucherUsageRepository;
    Clock clock;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.PARTNER_DASHBOARD, key = "@userService.getCurrentUser().getUserId()", unless = "#result == null")
    public PartnerDashboardResponse getDashboard() {
        User partner = userService.getCurrentUser();
        Long partnerId = partner.getUserId();

        LocalDate today = LocalDate.now(clock);
        LocalDateTime dayFrom = today.minusDays(REDEMPTION_DAYS - 1L).atStartOfDay();
        LocalDateTime dayTo = today.plusDays(1).atStartOfDay();

        // 1. Subscription Info & Usages
        List<Invoice> invoices = invoiceRepository.findByPartnerInfo_User_UserIdOrderByCreatedAtDesc(partnerId);
        Optional<Invoice> activeInvoiceOpt = invoices.stream()
                .filter(inv -> InvoiceStatus.ACTIVE.equals(inv.getStatus()))
                .findFirst();
        Invoice latestInvoice = activeInvoiceOpt.orElseGet(() -> invoices.isEmpty() ? null : invoices.get(0));

        SubscriptionInfo subInfo = null;
        List<UsageItem> usageItems = List.of();

        if (latestInvoice != null) {
            subInfo = SubscriptionInfo.builder()
                    .planName(latestInvoice.getSubscriptionPlan() != null ? latestInvoice.getSubscriptionPlan().getSubscriptionPlanName() : null)
                    .status(latestInvoice.getStatus())
                    .paymentStatus(latestInvoice.getPaymentStatus())
                    .billingCycle(latestInvoice.getBillingCycle())
                    .startDate(latestInvoice.getStartDate())
                    .endDate(latestInvoice.getEndDate())
                    .paidAmount(latestInvoice.getPaidAmount())
                    .build();

            List<SubscriptionUsage> usages = subscriptionUsageRepository.findByInvoice_InvoiceId(latestInvoice.getInvoiceId());
            usageItems = usages.stream()
                    .map(u -> UsageItem.builder()
                            .usageKey(u.getUsageKey())
                            .currentUsage(u.getCurrentUsage())
                            .maxAllowed(u.getMaxAllowed())
                            .build())
                    .toList();
        }

        // 2. Voucher Summary
        VoucherSummaryProjection summaryProj = voucherRepository.summarizeVouchersByPartner(partnerId);
        VoucherSummary voucherSummary = VoucherSummary.builder()
                .totalVouchers(nz(summaryProj != null ? summaryProj.getTotalVouchers() : null))
                .activeVouchers(nz(summaryProj != null ? summaryProj.getActiveVouchers() : null))
                .expiredVouchers(nz(summaryProj != null ? summaryProj.getExpiredVouchers() : null))
                .outOfStockVouchers(nz(summaryProj != null ? summaryProj.getOutOfStockVouchers() : null))
                .build();

        // 3. Redemption Trend (7 days)
        List<DailyCountProjection> dailyRows = voucherUsageRepository.countRedemptionsPerDay(partnerId, dayFrom, dayTo);
        List<RedemptionPoint> redemptionTrend = buildRedemptionTrend(today, dailyRows);

        // 4. Top Vouchers
        List<TopVoucherProjection> topProjs = voucherUsageRepository.findTopRedeemedVouchers(partnerId, PageRequest.of(0, TOP_VOUCHER_LIMIT));
        List<TopVoucher> topVouchers = topProjs.stream()
                .map(p -> TopVoucher.builder()
                        .voucherId(p.getVoucherId())
                        .voucherName(p.getVoucherName())
                        .voucherCode(p.getVoucherCode())
                        .redemptionCount(nz(p.getRedemptionCount()))
                        .build())
                .toList();

        return PartnerDashboardResponse.builder()
                .subscriptionInfo(subInfo)
                .usageItems(usageItems)
                .voucherSummary(voucherSummary)
                .redemptionTrend(redemptionTrend)
                .topVouchers(topVouchers)
                .build();
    }

    private List<RedemptionPoint> buildRedemptionTrend(LocalDate today, List<DailyCountProjection> rows) {
        Map<LocalDate, Long> byDate = rows.stream().collect(Collectors.toMap(
                r -> LocalDate.of(r.getBucketYear(), r.getBucketMonth(), r.getBucketDay()),
                r -> nz(r.getTotal())));
        List<RedemptionPoint> points = new ArrayList<>(REDEMPTION_DAYS);
        for (int i = REDEMPTION_DAYS - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            points.add(RedemptionPoint.builder()
                    .dayLabel(VN_DAY_LABELS.get(date.getDayOfWeek()))
                    .value(byDate.getOrDefault(date, 0L))
                    .date(date)
                    .build());
        }
        return points;
    }

    private long nz(Long value) {
        return value != null ? value : 0L;
    }
}
