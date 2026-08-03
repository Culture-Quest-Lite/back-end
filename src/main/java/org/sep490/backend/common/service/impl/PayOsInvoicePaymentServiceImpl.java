package org.sep490.backend.common.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.service.InvoiceActivationService;
import org.sep490.backend.common.service.PayOsInvoicePaymentService;
import org.sep490.backend.config.payos.PayOsProperties;
import org.sep490.backend.module.admin.dto.response.PaymentInitResponse;
import org.sep490.backend.module.admin.entity.Invoice;
import org.sep490.backend.module.admin.entity.SystemTransaction;
import org.sep490.backend.module.admin.entity.enumeration.InvoicePaymentStatus;
import org.sep490.backend.module.admin.entity.enumeration.PaymentGateway;
import org.sep490.backend.module.admin.entity.enumeration.SystemTransactionStatus;
import org.sep490.backend.module.admin.entity.enumeration.SystemTransactionType;
import org.sep490.backend.module.admin.repository.InvoiceRepository;
import org.sep490.backend.module.admin.repository.SystemTransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.concurrent.ThreadLocalRandom;

import vn.payos.PayOS;
import vn.payos.exception.PayOSException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayOsInvoicePaymentServiceImpl implements PayOsInvoicePaymentService {

    private final PayOS payOS;
    private final PayOsProperties payOsProperties;
    private final InvoiceRepository invoiceRepository;
    private final SystemTransactionRepository systemTransactionRepository;
    private final InvoiceActivationService invoiceActivationService;

    @Override
    public PaymentInitResponse initiatePayOsPayment(Invoice invoice, String redirectUrl) {

            long orderCode = generateOrderCode(invoice);
            String description = "SUB" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
            String effectiveReturnUrl = (redirectUrl != null && !redirectUrl.isBlank())
                    ? redirectUrl
                    : payOsProperties.getReturnUrl();

            CreatePaymentLinkRequest paymentData = CreatePaymentLinkRequest.builder()
                    .orderCode(orderCode)
                    .amount(invoice.getPaidAmount())
                    .description(description)
                    .returnUrl(effectiveReturnUrl)
                    .cancelUrl(payOsProperties.getCancelUrl())
                    .build();

            try {
                CreatePaymentLinkResponse response = payOS.paymentRequests().create(paymentData);
                invoice.setPayosOrderCode(orderCode);
                invoice.setPayosPaymentLinkId(response.getPaymentLinkId());
                invoice.setPaymentGateway(PaymentGateway.PAYOS);
                invoiceRepository.save(invoice);

                SystemTransaction paymentTrans = SystemTransaction.builder()
                        .invoice(invoice)
                        .transactionType(SystemTransactionType.PAYMENT)
                        .amount(invoice.getPaidAmount())
                        .status(SystemTransactionStatus.PENDING)
                        .gatewayRef(String.valueOf(orderCode))
                        .notes("Khởi tạo thanh toán qua PayOS")
                        .build();
                systemTransactionRepository.save(paymentTrans);

                return PaymentInitResponse.builder()
                        .subscriptionId(invoice.getInvoiceId())
                        .gateway(PaymentGateway.PAYOS)
                        .checkoutUrl(response.getCheckoutUrl())
                        .qrCode(response.getQrCode())
                        .amount(invoice.getPaidAmount())
                        .orderInfo(description)
                        .build();
            } catch (PayOSException e) {
                log.error("[PayOS] Tạo link thanh toán thất bại: {}", e.getMessage());
                throw new BusinessException("Không thể tạo link thanh toán PayOS: " + e.getMessage());
            }
    }

    private long generateOrderCode(Invoice invoice) {
        Long invoiceId = invoice.getInvoiceId();
        if (invoiceId == null) {
            throw new BusinessException("Hóa đơn phải được lưu trước khi tạo link thanh toán");
        }
        long secondOfDay = LocalTime.now().toSecondOfDay();
        return invoiceId * 100_000L + (secondOfDay % 100_000L);
    }

    @Override
    public boolean reconcileInvoiceWithPayOs(Invoice invoice) {
        if (InvoicePaymentStatus.PAID.equals(invoice.getPaymentStatus())) {
            return true;
        }
        if (invoice.getPayosOrderCode() == null) {
            throw new BusinessException("Hóa đơn chưa được khởi tạo thanh toán");
        }

        PaymentLink paymentLink;
        try {
            paymentLink = payOS.paymentRequests().get(invoice.getPayosOrderCode());
        } catch (PayOSException e) {
            log.error("[PayOS] Đối soát hóa đơn {} thất bại: {}", invoice.getInvoiceId(), e.getMessage());
            throw new BusinessException("Không thể kiểm tra trạng thái thanh toán từ PayOS: " + e.getMessage());
        }

        PaymentLinkStatus status = paymentLink.getStatus();
        if (PaymentLinkStatus.PAID.equals(status)) {
            String reference = paymentLink.getTransactions() == null || paymentLink.getTransactions().isEmpty()
                    ? null
                    : paymentLink.getTransactions().get(0).getReference();
            invoiceActivationService.markInvoicePaid(invoice, reference);
            return true;
        }

        if (PaymentLinkStatus.CANCELLED.equals(status) || PaymentLinkStatus.EXPIRED.equals(status)
                || PaymentLinkStatus.FAILED.equals(status)) {
            invoiceActivationService.markInvoiceFailed(invoice);
        }
        return false;
    }
}
