package org.sep490.backend.module.admin.dto.projection;

public interface RevenueSummaryProjection {
    Long getTotalRevenue();
    Long getPartnerRevenue();
    Long getPremiumRevenue();
    Long getRevenueThisMonth();
    Long getRevenueLastMonth();
    Long getPaidInvoices();
    Long getActivePartnerSubscriptions();
    Long getActivePremiumSubscriptions();
}
