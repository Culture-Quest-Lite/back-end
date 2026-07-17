package org.sep490.backend.module.admin.service.impl;

import jakarta.mail.internet.MimeMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.config.keycloak.KeyCloakAuthClient;
import org.sep490.backend.config.momo.MomoClient;
import org.sep490.backend.config.payos.PayOsProperties;
import org.sep490.backend.module.admin.dto.request.MomoIpnRequest;
import org.sep490.backend.module.admin.dto.request.PartnerSubscriptionRequest;
import org.sep490.backend.module.admin.dto.response.*;
import org.sep490.backend.module.admin.entity.*;
import org.sep490.backend.module.admin.entity.enumeration.BillingCycleEnum;
import org.sep490.backend.module.admin.entity.enumeration.InvoicePaymentStatus;
import org.sep490.backend.module.admin.entity.enumeration.InvoiceStatus;
import org.sep490.backend.module.admin.entity.enumeration.PartnerInfoStatus;
import org.sep490.backend.module.admin.entity.enumeration.PartnerApprovalStatus;
import org.sep490.backend.module.admin.entity.enumeration.PaymentGateway;
import org.sep490.backend.module.admin.entity.enumeration.SystemTransactionStatus;
import org.sep490.backend.module.admin.entity.enumeration.SystemTransactionType;
import org.sep490.backend.module.admin.mapper.PartnerSubscriptionMapper;
import org.sep490.backend.module.admin.repository.*;
import org.sep490.backend.module.admin.service.PartnerSubscriptionService;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.content.dto.response.MediaResponse;
import org.sep490.backend.module.content.entity.enumeration.MediaTargetType;
import org.sep490.backend.module.content.service.inter.MediaService;
import org.sep490.backend.module.content.service.inter.S3Service;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import vn.payos.PayOS;
import vn.payos.exception.PayOSException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.webhooks.WebhookData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PartnerSubscriptionServiceImpl implements PartnerSubscriptionService {
    SubscriptionPlanRepository planRepository;
    PartnerInfoRepository partnerInfoRepository;
    InvoiceRepository invoiceRepository;
    PartnerApprovalRepository partnerApprovalRepository;
    SubscriptionUsageRepository subscriptionUsageRepository;
    SystemTransactionRepository systemTransactionRepository;
    PartnerSubscriptionMapper subscriptionMapper;
    UserService userService;
    KeyCloakAuthClient keyCloakAuthClient;
    UserRepository userRepository;
    S3Service s3Service;
    MediaService mediaService;
    MomoClient momoClient;

    private final JavaMailSender mailSender;
    private final PayOsProperties payOsProperties;
    private final PayOS payOS;
    private final PlanRuleRepository planRuleRepository;
    @Value("${app.frontend-url:${FRONTEND_URL:http://localhost:3000}}")
    @NonFinal
    String frontendUrl;

    @Override
    @Transactional
    public PartnerSubscriptionResponse registerSubscription(PartnerSubscriptionRequest request) {
        User currentPartner = userService.getCurrentUser();
        SubscriptionPlan plan = planRepository.findById(request.getSubscriptionPlanId())
                .orElseThrow(() -> new BusinessException("Gói đăng ký không tồn tại"));

        // if
        // (!partnerInfoRepository.isLocationInVietnam(request.getLongitude(),
        // request.getLatitude())) {
        // throw new BusinessException("Vị trí của shop phải nằm trong lãnh thổ Việt
        // Nam");
        // }

        if (partnerInfoRepository.existsByShopEmail(request.getShopEmail())
                || userRepository.existsByEmail(request.getShopEmail())) {
            throw new BusinessException("Email quản lý shop này đã được đăng ký cho một tài khoản khác.");
        }

        long amount = BillingCycleEnum.MONTHLY.equals(request.getBillingCycle())
                ? (plan.getPriceMonthly() != null ? plan.getPriceMonthly() : 0L)
                : (plan.getPriceYearly() != null ? plan.getPriceYearly() : 0L);
        if (amount <= 0)
            throw new BusinessException("Giá gói không hợp lệ.");

        PartnerInfo partnerInfo = subscriptionMapper.toPartnerInfo(request);
        partnerInfo.setUser(currentPartner);
        partnerInfo.setStatus(PartnerInfoStatus.INACTIVE);

        if (request.getDocumentFile() != null && !request.getDocumentFile().isEmpty()) {
            try {
                String docUrl = s3Service.uploadFile(request.getDocumentFile(), "partner_subscriptions/documents");
                partnerInfo.setDocumentUrl(docUrl);
            } catch (IOException e) {
                throw new BusinessException("Lỗi xảy ra khi tải lên tài liệu xác minh lên S3: " + e.getMessage());
            }
        } else {
            throw new BusinessException("Giấy tờ xác minh là bắt buộc đối với đối tác");
        }
        partnerInfo = partnerInfoRepository.save(partnerInfo);

        Invoice invoice = Invoice.builder()
                .partnerInfo(partnerInfo)
                .subscriptionPlan(plan)
                .billingCycle(request.getBillingCycle())
                .status(InvoiceStatus.PENDING)
                .paymentStatus(InvoicePaymentStatus.PENDING)
                .paidAmount(amount)
                .invoiceCode("INV" + System.currentTimeMillis())
                .build();
        invoice = invoiceRepository.save(invoice);

        PartnerSubscriptionResponse response = subscriptionMapper.toResponse(invoice);
        if (request.getFiles() != null && request.getFiles().length > 0) {
            try {
                List<MediaResponse> mediaResponses = mediaService.uploadAndSaveMedias(
                        request.getFiles(), MediaTargetType.PARTNER_SUBSCRIPTION, partnerInfo.getPartnerInfoId());
                List<PartnerSubscriptionResponse.MediaDto> mediaDtos = mediaResponses.stream().map(m -> {
                    PartnerSubscriptionResponse.MediaDto dto = new PartnerSubscriptionResponse.MediaDto();
                    dto.setMediaId(m.getMediaId());
                    dto.setFileUrl(m.getFileUrl());
                    dto.setFileName(m.getFileName());
                    dto.setMediaType(m.getMediaType());
                    return dto;
                }).toList();
                response.setMedias(mediaDtos);
            } catch (IOException e) {
                throw new BusinessException("Lỗi tải lên media: " + e.getMessage());
            }
        }
        return response;
    }

    @Override
    @Transactional
    public PartnerSubscriptionResponse verifiedSubscription(Long subscriptionId, boolean isApproved) {
        Invoice invoice = invoiceRepository.findById(subscriptionId)
                .orElseThrow(() -> new BusinessException("Hóa đơn đăng ký gói không tồn tại"));

        if (!InvoiceStatus.PENDING.equals(invoice.getStatus())) {
            throw new BusinessException("Chỉ có thể duyệt hóa đơn dịch vụ đang ở trạng thái chờ duyệt");
        }

        PartnerInfo partnerInfo = invoice.getPartnerInfo();

        if (isApproved) {
            if (userRepository.existsByEmail(partnerInfo.getShopEmail())) {
                throw new BusinessException(
                        "Không thể duyệt: Email shop cung cấp đã bị trùng lặp với người dùng khác trong hệ thống.");
            }
            invoice.setStatus(InvoiceStatus.ACTIVE);
            partnerInfo.setStatus(PartnerInfoStatus.ACTIVE);
            LocalDateTime now = LocalDateTime.now();
            invoice.setStartDate(now);

            if (BillingCycleEnum.MONTHLY.equals(invoice.getBillingCycle())) {
                invoice.setEndDate(now.plusMonths(1));
            } else if (BillingCycleEnum.YEARLY.equals(invoice.getBillingCycle())) {
                invoice.setEndDate(now.plusYears(1));
            }

            PartnerApproval approval = PartnerApproval.builder()
                    .partnerInfo(partnerInfo)
                    .reviewer(userService.getCurrentUser())
                    .approvalStatus(PartnerApprovalStatus.APPROVED)
                    .reviewedAt(now)
                    .build();
            partnerApprovalRepository.save(approval);

            List<PlanRule> rules = planRuleRepository
                    .findBySubscriptionPlan_SubscriptionPlanId(invoice.getSubscriptionPlan().getSubscriptionPlanId());
            for (PlanRule rule : rules) {
                try {
                    int maxVal = Integer.parseInt(rule.getRuleValue());
                    SubscriptionUsage usage = SubscriptionUsage.builder()
                            .invoice(invoice)
                            .usageKey(rule.getRuleKey())
                            .currentUsage(0)
                            .maxAllowed(maxVal)
                            .resetAt(invoice.getEndDate())
                            .build();
                    subscriptionUsageRepository.save(usage);
                } catch (NumberFormatException e) {
                    log.error("Lỗi parse cấu hình giới hạn gói: key={}, value={}", rule.getRuleKey(),
                            rule.getRuleValue());
                }
            }

            createPartnerSubAccount(invoice);
        } else {
            invoice.setStatus(InvoiceStatus.CANCELLED);
            partnerInfo.setStatus(PartnerInfoStatus.INACTIVE);

            PartnerApproval approval = PartnerApproval.builder()
                    .partnerInfo(partnerInfo)
                    .reviewer(userService.getCurrentUser())
                    .approvalStatus(PartnerApprovalStatus.REJECTED)
                    .reviewedAt(LocalDateTime.now())
                    .build();
            partnerApprovalRepository.save(approval);

            if (InvoicePaymentStatus.PAID.equals(invoice.getPaymentStatus())
                    && invoice.getMomoTransId() != null) {
                doMomoRefund(invoice);
            }
        }
        partnerInfoRepository.save(partnerInfo);
        invoice = invoiceRepository.save(invoice);
        return subscriptionMapper.toResponse(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartnerSubscriptionResponse> getMySubscriptions() {
        User currentPartner = userService.getCurrentUser();
        List<Invoice> invoices = invoiceRepository
                .findByPartnerInfo_User_UserIdOrderByCreatedAtDesc(currentPartner.getUserId());
        return invoices.stream()
                .map(this::toResponseWithMedia)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartnerSubscriptionResponse> getSubscriptionsByPartnerId(Long partnerId) {
        userService.getProfile(partnerId);
        List<Invoice> invoices = invoiceRepository
                .findByPartnerInfo_User_UserIdOrderByCreatedAtDesc(partnerId);
        return invoices.stream()
                .map(this::toResponseWithMedia)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartnerSubscriptionResponse> getAllSubscriptions(InvoiceStatus status) {
        List<Invoice> invoices = status != null
                ? invoiceRepository.findByStatusOrderByCreatedAtDesc(status)
                : invoiceRepository.findAllByOrderByCreatedAtDesc();
        return invoices.stream()
                .map(this::toResponseWithMedia)
                .toList();
    }

    private PartnerSubscriptionResponse toResponseWithMedia(Invoice invoice) {
        PartnerSubscriptionResponse response = subscriptionMapper.toResponse(invoice);
        PartnerInfo partnerInfo = invoice.getPartnerInfo();
        if (partnerInfo != null && partnerInfo.getMedias() != null) {
            response.setMedias(partnerInfo.getMedias().stream()
                    .map(subscriptionMapper::toMediaDto)
                    .toList());
        }
        return response;
    }

    @Override
    @Deprecated
    @Transactional
    public MomoPaymentInitResponse initiatePayment(Long subscriptionId, String redirectUrl) {
        PaymentInitResponse resp = initiatePayment(subscriptionId, redirectUrl, "MOMO");
        return MomoPaymentInitResponse.builder()
                .subscriptionId(resp.getSubscriptionId())
                .payUrl(resp.getPayUrl())
                .deeplink(resp.getDeeplink())
                .qrCodeUrl(resp.getQrCodeUrl())
                .amount(resp.getAmount())
                .orderInfo(resp.getOrderInfo())
                .build();
    }

    @Override
    @Transactional
    public void handleMomoIpn(MomoIpnRequest request) {
        Invoice invoice = invoiceRepository
                .findByMomoOrderId(request.getOrderId())
                .orElse(null);

        if (invoice == null) {
            log.error("[MoMo IPN] Không tìm thấy hóa đơn với orderId={}", request.getOrderId());
            return;
        }

        if (InvoicePaymentStatus.PAID.equals(invoice.getPaymentStatus())) {
            log.warn("[MoMo IPN] Đã xử lý rồi, bỏ qua. orderId={}", request.getOrderId());
            return;
        }

        SystemTransaction transaction = systemTransactionRepository
                .findFirstByGatewayRefOrderByCreatedAtDesc(request.getOrderId())
                .orElse(null);

        if (request.getResultCode() == 0) {
            invoice.setPaymentStatus(InvoicePaymentStatus.PAID);
            invoice.setMomoTransId(String.valueOf(request.getTransId()));
            invoice.setPaidAt(LocalDateTime.now());
            invoice.setStatus(InvoiceStatus.PENDING);
            invoiceRepository.save(invoice);

            if (transaction != null) {
                transaction.setStatus(SystemTransactionStatus.SUCCESSED);
                transaction.setNotes("Thanh toán thành công qua MoMo. Mã GD: " + request.getTransId());
                systemTransactionRepository.save(transaction);
            }
        } else {
            invoice.setPaymentStatus(InvoicePaymentStatus.FAILED);
            invoiceRepository.save(invoice);

            if (transaction != null) {
                transaction.setStatus(SystemTransactionStatus.FAILED);
                transaction.setNotes("Thanh toán thất bại qua MoMo. Lỗi: " + request.getMessage());
                systemTransactionRepository.save(transaction);
            }
        }
    }

    @Override
    @Transactional
    public PaymentInitResponse initiatePayment(Long subscriptionId, String redirectUrl, String gateway) {
        User currentUser = userService.getCurrentUser();
        Invoice invoice = invoiceRepository.findById(subscriptionId)
                .orElseThrow(() -> new BusinessException("Hóa đơn không tồn tại"));

        if (!invoice.getPartnerInfo().getUser().getUserId().equals(currentUser.getUserId())) {
            throw new BusinessException("Bạn không có quyền thực hiện thao tác này.");
        }
        if (InvoicePaymentStatus.PAID.equals(invoice.getPaymentStatus())) {
            throw new BusinessException("Hóa đơn này đã được thanh toán.");
        }
        if (invoice.getPartnerInfo().getDocumentUrl() == null) {
            throw new BusinessException("Vui lòng upload đầy đủ giấy tờ trước khi thanh toán.");
        }

        if ("PAYOS".equalsIgnoreCase(gateway)) {
            return initiatePayOsPayment(invoice, redirectUrl);
        } else {
            return initiateMomoPayment(invoice, redirectUrl);
        }
    }

    @Override
    @Transactional
    public void handlePayOsWebhook(Map<String, Object> body) {
        try {
            WebhookData data = payOS.webhooks().verify(body);

            long orderCode = data.getOrderCode();
            Invoice invoice = invoiceRepository.findByPayosOrderCode(orderCode)
                    .orElse(null);
            if (invoice == null) {
                log.error("[PayOS Webhook] Không tìm thấy hóa đơn với orderCode={}", orderCode);
                return;
            }
            if (InvoicePaymentStatus.PAID.equals(invoice.getPaymentStatus())) {
                log.warn("[PayOS Webhook] Đã xử lý rồi, bỏ qua. orderCode={}", orderCode);
                return;
            }

            SystemTransaction transaction = systemTransactionRepository
                    .findFirstByGatewayRefOrderByCreatedAtDesc(String.valueOf(orderCode))
                    .orElse(null);

            if ("00".equals(data.getCode())) {
                invoice.setPaymentStatus(InvoicePaymentStatus.PAID);
                invoice.setPayosTransactionId(data.getReference());
                invoice.setPaidAt(LocalDateTime.now());
                invoice.setStatus(InvoiceStatus.PENDING);
                invoiceRepository.save(invoice);

                if (transaction != null) {
                    transaction.setStatus(SystemTransactionStatus.SUCCESSED);
                    transaction.setNotes("Thanh toán thành công qua PayOS. Ref: " + data.getReference());
                    systemTransactionRepository.save(transaction);
                }
            } else {
                invoice.setPaymentStatus(InvoicePaymentStatus.FAILED);
                invoiceRepository.save(invoice);

                if (transaction != null) {
                    transaction.setStatus(SystemTransactionStatus.FAILED);
                    transaction.setNotes("Thanh toán thất bại qua PayOS.");
                    systemTransactionRepository.save(transaction);
                }
            }
        } catch (Exception e) {
            log.error("[PayOS Webhook] Lỗi xác thực webhook: {}", e.getMessage());
        }
    }

    private PaymentInitResponse initiateMomoPayment(Invoice invoice, String redirectUrl) {
        long amount = invoice.getPaidAmount();
        String momoOrderId = "SUB_" + invoice.getInvoiceId() + "_" + System.currentTimeMillis();
        String momoRequestId = UUID.randomUUID().toString();
        String orderInfo = "Thanh toan dang ky goi dich vu Culture Quest cho hoa don SUB" + invoice.getInvoiceId();

        MomoPaymentResponse momoResp;
        try {
            momoResp = momoClient.createPayment(amount, momoOrderId, momoRequestId, orderInfo, redirectUrl);
        } catch (Exception e) {
            throw new BusinessException("Không thể kết nối cổng thanh toán MoMo. Vui lòng thử lại.");
        }
        if (momoResp == null || momoResp.getResultCode() != 0) {
            String errMsg = momoResp != null ? momoResp.getMessage() : "Không có phản hồi";
            throw new BusinessException("Tạo giao dịch MoMo thất bại: " + errMsg);
        }

        invoice.setMomoOrderId(momoOrderId);
        invoice.setMomoRequestId(momoRequestId);
        invoice.setPaymentGateway(PaymentGateway.MOMO);
        invoiceRepository.save(invoice);

        SystemTransaction paymentTrans = SystemTransaction.builder()
                .invoice(invoice)
                .transactionType(SystemTransactionType.PAYMENT)
                .amount(amount)
                .status(SystemTransactionStatus.PENDING)
                .gatewayRef(momoOrderId)
                .notes("Khởi tạo thanh toán qua MoMo")
                .build();
        systemTransactionRepository.save(paymentTrans);

        return PaymentInitResponse.builder()
                .subscriptionId(invoice.getInvoiceId())
                .gateway(PaymentGateway.MOMO)
                .payUrl(momoResp.getPayUrl())
                .deeplink(momoResp.getDeeplink())
                .qrCodeUrl(momoResp.getQrCodeUrl())
                .amount(amount)
                .orderInfo(orderInfo)
                .build();
    }

    private PaymentInitResponse initiatePayOsPayment(Invoice invoice, String redirectUrl) {
        long orderCode = System.currentTimeMillis() / 1000;
        String description = "SUB" + invoice.getInvoiceId();
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

    private void doMomoRefund(Invoice invoice) {
        String refundOrderId = "REFUND_" + invoice.getInvoiceId() + "_" + System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        try {
            MomoRefundResponse resp = momoClient.refund(
                    invoice.getPaidAmount(),
                    refundOrderId,
                    requestId,
                    Long.parseLong(invoice.getMomoTransId()),
                    "Hoan tien don dang ky bi tu choi");
            if (resp != null && resp.getResultCode() == 0) {
                invoice.setPaymentStatus(InvoicePaymentStatus.REFUNDED);
                invoice.setStatus(InvoiceStatus.CANCELLED);
                invoice.setRefundOrderId(refundOrderId);
                invoice.setRefundedAt(LocalDateTime.now());

                SystemTransaction refundTrans = SystemTransaction.builder()
                        .invoice(invoice)
                        .transactionType(SystemTransactionType.REFUND)
                        .amount(invoice.getPaidAmount())
                        .status(SystemTransactionStatus.SUCCESSED)
                        .gatewayRef(refundOrderId)
                        .notes("Hoàn tiền thành công qua MoMo.")
                        .build();
                systemTransactionRepository.save(refundTrans);
            } else {
                String msg = resp != null ? resp.getMessage() : "null response";
                log.error("[MoMo] Refund FAILED: invoiceId={}, msg={}",
                        invoice.getInvoiceId(), msg);

                SystemTransaction refundTrans = SystemTransaction.builder()
                        .invoice(invoice)
                        .transactionType(SystemTransactionType.REFUND)
                        .amount(invoice.getPaidAmount())
                        .status(SystemTransactionStatus.FAILED)
                        .gatewayRef(refundOrderId)
                        .notes("Hoàn tiền thất bại qua MoMo. Lỗi: " + msg)
                        .build();
                systemTransactionRepository.save(refundTrans);
            }
        } catch (Exception e) {
            log.error("[MoMo] Exception khi refund invoiceId={}: {}",
                    invoice.getInvoiceId(), e.getMessage(), e);
        }
    }

    private void createPartnerSubAccount(Invoice invoice) {
        User owner = invoice.getPartnerInfo().getUser();
        String shopEmail = invoice.getPartnerInfo().getShopEmail();

        String rawPassword = UUID.randomUUID().toString().substring(0, 8);
        String partnerUsername = "shop_" + owner.getUsername();

        try {
            String keycloakUserId = keyCloakAuthClient.createUser(
                    partnerUsername,
                    shopEmail,
                    owner.getDisplayName(),
                    rawPassword,
                    List.of("PARTNER"));

            User partnerAccount = User.builder()
                    .keycloakUserId(keycloakUserId)
                    .username(partnerUsername)
                    .email(shopEmail)
                    .displayName(owner.getDisplayName())
                    .status(UserStatus.ACTIVE)
                    .role(UserRole.PARTNER)
                    .build();
            userRepository.save(partnerAccount);
            sendPartnerCredentialsEmail(owner.getEmail(), shopEmail, partnerUsername, rawPassword,
                    owner.getDisplayName());
        } catch (Exception e) {
            throw new BusinessException("Lỗi hệ thống khi tạo tài khoản quản lý cho Partner: " + e.getMessage());
        }
    }

    private void sendPartnerCredentialsEmail(String ownerEmail, String shopEmail, String username, String password,
            String ownerName) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(ownerEmail);
        helper.setSubject("[CULTURE QUEST LITE] THÔNG TIN TÀI KHOẢN QUẢN LÝ SHOP");

        ClassPathResource resource = new ClassPathResource("templates/partner-account-email.html");
        String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);

        content = content.replace("{{OWNER_NAME}}", ownerName != null ? ownerName : "Đối tác");
        content = content.replace("{{SHOP_EMAIL}}", shopEmail);
        content = content.replace("{{USERNAME}}", username);
        content = content.replace("{{PASSWORD}}", password);
        content = content.replace("{{LOGIN_LINK}}", frontendUrl + "/login");

        helper.setText(content, true);
        mailSender.send(message);
    }
}