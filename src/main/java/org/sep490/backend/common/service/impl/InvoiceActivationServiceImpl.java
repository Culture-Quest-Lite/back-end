package org.sep490.backend.common.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.common.service.InvoiceActivationService;
import org.sep490.backend.module.admin.entity.Invoice;
import org.sep490.backend.module.admin.entity.SystemTransaction;
import org.sep490.backend.module.admin.entity.enumeration.BillingCycleEnum;
import org.sep490.backend.module.admin.entity.enumeration.InvoicePaymentStatus;
import org.sep490.backend.module.admin.entity.enumeration.InvoiceStatus;
import org.sep490.backend.module.admin.entity.enumeration.SystemTransactionStatus;
import org.sep490.backend.module.admin.repository.InvoiceRepository;
import org.sep490.backend.module.admin.repository.SystemTransactionRepository;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceActivationServiceImpl implements InvoiceActivationService {

    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final SystemTransactionRepository systemTransactionRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markInvoicePaid(Invoice invoice, String gatewayRef) {
        if (InvoicePaymentStatus.PAID.equals(invoice.getPaymentStatus())) {
            log.warn("[Invoice] Hóa đơn {} đã được xử lý trước đó, bỏ qua", invoice.getInvoiceId());
            return false;
        }

        invoice.setPaymentStatus(InvoicePaymentStatus.PAID);
        invoice.setPayosTransactionId(gatewayRef);
        invoice.setPaidAt(LocalDateTime.now());

        if (invoice.getUser() != null) {
            activatePremium(invoice);
        } else {
            invoice.setStatus(InvoiceStatus.PENDING);
        }
        invoiceRepository.save(invoice);
        updateTransaction(invoice, SystemTransactionStatus.SUCCESSED,
                "Thanh toán thành công qua PayOS. Ref: " + gatewayRef);
        return true;
    }

    private void activatePremium(Invoice invoice) {
        User buyer = invoice.getUser();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = invoiceRepository
                .findFirstByUser_UserIdAndStatusOrderByEndDateDesc(buyer.getUserId(), InvoiceStatus.ACTIVE)
                .map(Invoice::getEndDate)
                .filter(d -> d != null && d.isAfter(now))
                .orElse(now);

        invoice.setStatus(InvoiceStatus.ACTIVE);
        invoice.setStartDate(start);
        invoice.setEndDate(BillingCycleEnum.MONTHLY.equals(invoice.getBillingCycle())
                ? start.plusMonths(1)
                : start.plusYears(1));

        buyer.setIsPremium(true);
        userRepository.save(buyer);
        log.info("[Premium] Đã nâng cấp user {} lên Premium, hạn tới {}", buyer.getUserId(), invoice.getEndDate());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markInvoiceFailed(Invoice invoice) {
        if (InvoicePaymentStatus.PAID.equals(invoice.getPaymentStatus())) {
            log.warn("[Invoice] Bỏ qua đánh dấu thất bại: hóa đơn {} đã thanh toán thành công",
                    invoice.getInvoiceId());
            return;
        }
        invoice.setPaymentStatus(InvoicePaymentStatus.FAILED);
        invoiceRepository.save(invoice);
        updateTransaction(invoice, SystemTransactionStatus.FAILED, "Thanh toán thất bại qua PayOS.");
    }

    private void updateTransaction(Invoice invoice, SystemTransactionStatus status, String notes) {
        if (invoice.getPayosOrderCode() == null) {
            return;
        }
        systemTransactionRepository
                .findFirstByGatewayRefOrderByCreatedAtDesc(String.valueOf(invoice.getPayosOrderCode()))
                .ifPresent(transaction -> {
                    transaction.setStatus(status);
                    transaction.setNotes(notes);
                    systemTransactionRepository.save(transaction);
                });
    }
}
