package org.sep490.backend.common.service;

import org.sep490.backend.module.admin.entity.Invoice;

public interface InvoiceActivationService {

    boolean markInvoicePaid(Invoice invoice, String gatewayRef);

    void markInvoiceFailed(Invoice invoice);
}
