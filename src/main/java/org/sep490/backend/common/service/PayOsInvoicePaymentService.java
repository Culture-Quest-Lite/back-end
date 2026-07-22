package org.sep490.backend.common.service;

import org.sep490.backend.module.admin.dto.response.PaymentInitResponse;
import org.sep490.backend.module.admin.entity.Invoice;

public interface PayOsInvoicePaymentService {
    PaymentInitResponse initiatePayOsPayment(Invoice invoice, String redirectUrl);
}
