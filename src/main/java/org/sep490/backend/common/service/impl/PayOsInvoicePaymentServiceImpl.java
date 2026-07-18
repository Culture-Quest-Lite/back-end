package org.sep490.backend.common.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.service.PayOsInvoicePaymentService;
import org.sep490.backend.config.payos.PayOsProperties;
import org.sep490.backend.module.admin.dto.response.PaymentInitResponse;
import org.sep490.backend.module.admin.entity.Invoice;
import org.sep490.backend.module.admin.entity.SystemTransaction;
import org.sep490.backend.module.admin.entity.enumeration.PaymentGateway;
import org.sep490.backend.module.admin.entity.enumeration.SystemTransactionStatus;
import org.sep490.backend.module.admin.entity.enumeration.SystemTransactionType;
import org.sep490.backend.module.admin.repository.InvoiceRepository;
import org.sep490.backend.module.admin.repository.SystemTransactionRepository;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

import vn.payos.PayOS;
import vn.payos.exception.PayOSException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayOsInvoicePaymentServiceImpl implements PayOsInvoicePaymentService {

    private final PayOS payOS;
    private final PayOsProperties payOsProperties;
    private final InvoiceRepository invoiceRepository;
    private final SystemTransactionRepository systemTransactionRepository;

    @Override
    public PaymentInitResponse initiatePayOsPayment(Invoice invoice, String redirectUrl) {

            long orderCode = System.currentTimeMillis() / 1000;
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
}
