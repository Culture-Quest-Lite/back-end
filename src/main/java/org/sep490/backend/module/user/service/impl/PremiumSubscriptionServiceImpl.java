package org.sep490.backend.module.user.service.impl;

import lombok.RequiredArgsConstructor;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.service.PayOsInvoicePaymentService;
import org.sep490.backend.module.admin.dto.response.PaymentInitResponse;
import org.sep490.backend.module.admin.entity.Invoice;
import org.sep490.backend.module.admin.entity.SubscriptionPlan;
import org.sep490.backend.module.admin.entity.enumeration.*;
import org.sep490.backend.module.admin.repository.InvoiceRepository;
import org.sep490.backend.module.admin.repository.SubscriptionPlanRepository;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.user.dto.request.PremiumSubscribeRequest;
import org.sep490.backend.module.user.dto.response.PremiumSubscriptionResponse;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.sep490.backend.module.user.service.PremiumSubscriptionService;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PremiumSubscriptionServiceImpl implements PremiumSubscriptionService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserService userService;
    private final PayOsInvoicePaymentService payOsInvoicePaymentService;

    @Override
    @Transactional
    public PaymentInitResponse subscribe(PremiumSubscribeRequest request) {
        User user = userService.getCurrentUser();
        if (user.getRole() != UserRole.EXPLORER) {
            throw new BusinessException("Chỉ người dùng Explorer mới có thể mua gói Premium");
        }

        SubscriptionPlan plan = subscriptionPlanRepository.findById(request.getSubscriptionPlanId())
                .orElseThrow(() -> new BusinessException("Gói đăng ký không tồn tại"));
        if (plan.getPlanType() != PlanType.PREMIUM || plan.getStatus() != SubscriptionPlanStatus.ACTIVE) {
            throw new BusinessException("Gói này không dành cho người dùng Premium");
        }

        Long amount = BillingCycleEnum.MONTHLY.equals(request.getBillingCycle())
                ? plan.getPriceMonthly() : plan.getPriceYearly();
        if (amount == null || amount <= 0) {
            throw new BusinessException("Giá gói không hợp lệ");
        }

        Invoice invoice = Invoice.builder()
                .user(user)
                .subscriptionPlan(plan)
                .billingCycle(request.getBillingCycle())
                .status(InvoiceStatus.PENDING)
                .paymentStatus(InvoicePaymentStatus.PENDING)
                .paidAmount(amount)
                .invoiceCode("INV" + System.currentTimeMillis())
                .build();
        invoice = invoiceRepository.save(invoice);

        return payOsInvoicePaymentService.initiatePayOsPayment(invoice, request.getRedirectUrl());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PremiumSubscriptionResponse> getMyPremiumSubscription() {
        User user = userService.getCurrentUser();
        return invoiceRepository.findByUser_UserIdOrderByCreatedAtDesc(user.getUserId())
                .stream()
                .map(inv -> PremiumSubscriptionResponse.builder()
                        .invoiceId(inv.getInvoiceId())
                        .planName(inv.getSubscriptionPlan().getSubscriptionPlanName())
                        .billingCycle(inv.getBillingCycle())
                        .status(inv.getStatus())
                        .paymentStatus(inv.getPaymentStatus())
                        .startDate(inv.getStartDate())
                        .endDate(inv.getEndDate())
                        .paidAmount(inv.getPaidAmount())
                        .build())
                .toList();
    }
}
