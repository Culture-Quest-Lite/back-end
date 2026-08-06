package org.sep490.backend.module.partner.dto.projection;

public interface TopVoucherProjection {
    Long getVoucherId();
    String getVoucherName();
    String getVoucherCode();
    Long getRedemptionCount();
}
