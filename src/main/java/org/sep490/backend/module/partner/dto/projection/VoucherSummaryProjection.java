package org.sep490.backend.module.partner.dto.projection;

public interface VoucherSummaryProjection {
    Long getTotalVouchers();
    Long getActiveVouchers();
    Long getExpiredVouchers();
    Long getOutOfStockVouchers();
}
