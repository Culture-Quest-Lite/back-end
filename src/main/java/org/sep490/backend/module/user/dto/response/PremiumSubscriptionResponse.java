package org.sep490.backend.module.user.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.admin.entity.enumeration.BillingCycleEnum;
import org.sep490.backend.module.admin.entity.enumeration.InvoicePaymentStatus;
import org.sep490.backend.module.admin.entity.enumeration.InvoiceStatus;

import java.time.LocalDateTime;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PremiumSubscriptionResponse {

    Long invoiceId;
    String planName;
    BillingCycleEnum billingCycle;
    InvoiceStatus status;
    InvoicePaymentStatus paymentStatus;
    LocalDateTime startDate;
    LocalDateTime endDate;
    Long paidAmount;
}
