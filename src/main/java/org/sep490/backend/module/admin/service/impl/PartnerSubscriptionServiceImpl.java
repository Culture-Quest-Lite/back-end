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
import org.sep490.backend.module.admin.entity.PartnerSubscription;
import org.sep490.backend.module.admin.entity.SubscriptionPlan;
import org.sep490.backend.module.admin.entity.enumeration.BillingCycleEnum;
import org.sep490.backend.module.admin.entity.enumeration.MomoPaymentStatus;
import org.sep490.backend.module.admin.entity.enumeration.PartnerSubscriptionStatus;
import org.sep490.backend.module.admin.entity.enumeration.PaymentGateway;
import org.sep490.backend.module.admin.mapper.PartnerSubscriptionMapper;
import org.sep490.backend.module.admin.repository.PartnerSubscriptionRepository;
import org.sep490.backend.module.admin.repository.SubscriptionPlanRepository;
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
    PartnerSubscriptionRepository partnerSubscriptionRepository;
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
        // (!partnerSubscriptionRepository.isLocationInVietnam(request.getLongitude(),
        // request.getLatitude())) {
        // throw new BusinessException("Vị trí của shop phải nằm trong lãnh thổ Việt
        // Nam");
        // }

        if (userRepository.existsByEmail(request.getShopEmail())) {
            throw new BusinessException("Email quản lý shop này đã được đăng ký cho một tài khoản khác.");
        }

        long amount = BillingCycleEnum.MONTHLY.equals(request.getBillingCycle())
                ? (plan.getPriceMonthly() != null ? plan.getPriceMonthly() : 0L)
                : (plan.getPriceYearly() != null ? plan.getPriceYearly() : 0L);
        if (amount <= 0)
            throw new BusinessException("Giá gói không hợp lệ.");

        PartnerSubscription subscription = subscriptionMapper.toEntity(request);
        subscription.setPartner(currentPartner);
        subscription.setSubscriptionPlan(plan);
        subscription.setIsVerified(false);
        subscription.setStatus(PartnerSubscriptionStatus.PAYMENT_PENDING);
        subscription.setPaymentStatus(MomoPaymentStatus.PENDING);
        subscription.setPaidAmount(amount);
        subscription.setStartDate(null);
        subscription.setEndDate(null);

        if (request.getDocumentFile() != null && !request.getDocumentFile().isEmpty()) {
            try {
                String docUrl = s3Service.uploadFile(request.getDocumentFile(), "partner_subscriptions/documents");
                subscription.setDocumentUrl(docUrl);
            } catch (IOException e) {
                throw new BusinessException("Lỗi xảy ra khi tải lên tài liệu xác minh lên S3: " + e.getMessage());
            }
        } else {
            throw new BusinessException("Giấy tờ xác minh là bắt buộc đối với đối tác");
        }
        subscription = partnerSubscriptionRepository.save(subscription);

        PartnerSubscriptionResponse response = subscriptionMapper.toResponse(subscription);
        if (request.getFiles() != null && request.getFiles().length > 0) {
            try {
                List<MediaResponse> mediaResponses = mediaService.uploadAndSaveMedias(
                        request.getFiles(), MediaTargetType.PARTNER_SUBSCRIPTION, subscription.getId());
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
        PartnerSubscription subscription = partnerSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new BusinessException("Yêu cầu đăng ký gói không tồn tại"));

        if (!PartnerSubscriptionStatus.PENDING.equals(subscription.getStatus())) {
            throw new BusinessException("Chỉ có thể duyệt gói dịch vụ đang ở trạng thái chờ duyệt");
        }

        if (isApproved) {
            if (userRepository.existsByEmail(subscription.getShopEmail())) {
                throw new BusinessException(
                        "Không thể duyệt: Email shop cung cấp đã bị trùng lặp với người dùng khác trong hệ thống.");
            }
            subscription.setStatus(PartnerSubscriptionStatus.ACTIVE);
            subscription.setIsVerified(true);
            LocalDateTime now = LocalDateTime.now();
            subscription.setStartDate(now);

            if (BillingCycleEnum.MONTHLY.equals(subscription.getBillingCycle())) {
                subscription.setEndDate(now.plusMonths(1));
            } else if (BillingCycleEnum.YEARLY.equals(subscription.getBillingCycle())) {
                subscription.setEndDate(now.plusYears(1));
            }
            createPartnerSubAccount(subscription);
        } else {
            subscription.setStatus(PartnerSubscriptionStatus.REJECTED);
            subscription.setIsVerified(false);

            if (MomoPaymentStatus.PAID.equals(subscription.getPaymentStatus())
                    && subscription.getMomoTransId() != null) {
                doMomoRefund(subscription);
            }
        }
        subscription = partnerSubscriptionRepository.save(subscription);
        return subscriptionMapper.toResponse(subscription);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartnerSubscriptionResponse> getMySubscriptions() {
        User currentPartner = userService.getCurrentUser();
        List<PartnerSubscription> subscriptions = partnerSubscriptionRepository
                .findByPartner_UserIdOrderByCreatedAtDesc(currentPartner.getUserId());
        return subscriptions.stream()
                .map(subscriptionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartnerSubscriptionResponse> getSubscriptionsByPartnerId(Long partnerId) {
        userService.getProfile(partnerId);
        List<PartnerSubscription> subscriptions = partnerSubscriptionRepository
                .findByPartner_UserIdOrderByCreatedAtDesc(partnerId);
        return subscriptions.stream()
                .map(subscriptionMapper::toResponse)
                .toList();
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
        PartnerSubscription subscription = partnerSubscriptionRepository
                .findByMomoOrderId(request.getOrderId())
                .orElse(null);

        if (subscription == null) {
            log.error("[MoMo IPN] Không tìm thấy subscription với orderId={}", request.getOrderId());
            return;
        }

        if (MomoPaymentStatus.PAID.equals(subscription.getPaymentStatus())) {
            log.warn("[MoMo IPN] Đã xử lý rồi, bỏ qua. orderId={}", request.getOrderId());
            return;
        }

        if (request.getResultCode() == 0) {
            subscription.setPaymentStatus(MomoPaymentStatus.PAID);
            subscription.setMomoTransId(String.valueOf(request.getTransId()));
            subscription.setPaidAt(LocalDateTime.now());
            subscription.setStatus(PartnerSubscriptionStatus.PENDING);
            partnerSubscriptionRepository.save(subscription);
        } else {
            subscription.setPaymentStatus(MomoPaymentStatus.FAILED);
            subscription.setStatus(PartnerSubscriptionStatus.PAYMENT_FAILED);
            partnerSubscriptionRepository.save(subscription);
        }

    }

    @Override
    @Transactional
    public PaymentInitResponse initiatePayment(Long subscriptionId, String redirectUrl, String gateway) {
        User currentUser = userService.getCurrentUser();
        PartnerSubscription subscription = partnerSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new BusinessException("Subscription không tồn tại"));

        if (!subscription.getPartner().getUserId().equals(currentUser.getUserId())) {
            throw new BusinessException("Bạn không có quyền thực hiện thao tác này.");
        }
        if (!PartnerSubscriptionStatus.PAYMENT_PENDING.equals(subscription.getStatus())
                && !PartnerSubscriptionStatus.PAYMENT_FAILED.equals(subscription.getStatus())) {
            throw new BusinessException("Đơn đăng ký này không thể thanh toán lúc này.");
        }
        if (subscription.getDocumentUrl() == null) {
            throw new BusinessException("Vui lòng upload đầy đủ giấy tờ trước khi thanh toán.");
        }

        if ("PAYOS".equalsIgnoreCase(gateway)) {
            return initiatePayOsPayment(subscription, redirectUrl);
        } else {
            return initiateMomoPayment(subscription, redirectUrl);
        }
    }

    @Override
    public void handlePayOsWebhook(Map<String, Object> body) {
        try {
            WebhookData data = payOS.webhooks().verify(body);

            long orderCode = data.getOrderCode();
            PartnerSubscription subscription = partnerSubscriptionRepository.findByPayosOrderCode(orderCode)
                    .orElse(null);
            if (subscription == null) {
                log.error("[PayOS Webhook] Không tìm thấy subscription với orderCode={}", orderCode);
                return;
            }
            if (MomoPaymentStatus.PAID.equals(subscription.getPaymentStatus())) {
                log.warn("[PayOS Webhook] Đã xử lý rồi, bỏ qua. orderCode={}", orderCode);
                return;
            }

            if ("00".equals(data.getCode())) {
                subscription.setPaymentStatus(MomoPaymentStatus.PAID);
                subscription.setPayosTransactionId(data.getReference());
                subscription.setPaidAt(LocalDateTime.now());
                subscription.setStatus(PartnerSubscriptionStatus.PENDING);
                partnerSubscriptionRepository.save(subscription);
            } else {
                subscription.setPaymentStatus(MomoPaymentStatus.FAILED);
                subscription.setStatus(PartnerSubscriptionStatus.PAYMENT_FAILED);
            }
            partnerSubscriptionRepository.save(subscription);
        } catch (Exception e) {
            log.error("[PayOS Webhook] Lỗi xác thực webhook: {}", e.getMessage());
        }
    }

    private PaymentInitResponse initiateMomoPayment(PartnerSubscription subscription, String redirectUrl) {
        long amount = subscription.getPaidAmount();
        String momoOrderId = "SUB_" + subscription.getId() + "_" + System.currentTimeMillis();
        String momoRequestId = UUID.randomUUID().toString();
        String orderInfo = "Thanh toan goi "
                + subscription.getSubscriptionPlan().getSubscriptionPlanName()
                + " - " + subscription.getShopName();

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

        subscription.setMomoOrderId(momoOrderId);
        subscription.setMomoRequestId(momoRequestId);
        subscription.setPaymentGateway(PaymentGateway.MOMO);
        partnerSubscriptionRepository.save(subscription);

        return PaymentInitResponse.builder()
                .subscriptionId(subscription.getId())
                .gateway(PaymentGateway.MOMO)
                .payUrl(momoResp.getPayUrl())
                .deeplink(momoResp.getDeeplink())
                .qrCodeUrl(momoResp.getQrCodeUrl())
                .amount(amount)
                .orderInfo(orderInfo)
                .build();
    }

    private PaymentInitResponse initiatePayOsPayment(PartnerSubscription subscription, String redirectUrl) {
        long orderCode = System.currentTimeMillis() / 1000;
        String description = "SUB" + subscription.getId();
        String effectiveReturnUrl = (redirectUrl != null && !redirectUrl.isBlank())
                ? redirectUrl : payOsProperties.getReturnUrl();

        CreatePaymentLinkRequest paymentData = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(subscription.getPaidAmount())
                .description(description)
                .returnUrl(effectiveReturnUrl)
                .cancelUrl(payOsProperties.getCancelUrl())
                .build();

        try {
            CreatePaymentLinkResponse response = payOS.paymentRequests().create(paymentData);
            subscription.setPayosOrderCode(orderCode);
            subscription.setPayosPaymentLinkId(response.getPaymentLinkId());
            subscription.setPaymentGateway(PaymentGateway.PAYOS);
            partnerSubscriptionRepository.save(subscription);

            return PaymentInitResponse.builder()
                    .subscriptionId(subscription.getId())
                    .gateway(PaymentGateway.PAYOS)
                    .checkoutUrl(response.getCheckoutUrl())
                    .qrCode(response.getQrCode())
                    .amount(subscription.getPaidAmount())
                    .orderInfo(description)
                    .build();
        } catch (PayOSException e) {
            log.error("[PayOS] Tạo link thanh toán thất bại: {}", e.getMessage());
            throw new BusinessException("Không thể tạo link thanh toán PayOS: " + e.getMessage());
        }
    }

    private void doMomoRefund(PartnerSubscription subscription) {
        String refundOrderId = "REFUND_" + subscription.getId() + "_" + System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        try {
            MomoRefundResponse resp = momoClient.refund(
                    subscription.getPaidAmount(),
                    refundOrderId,
                    requestId,
                    Long.parseLong(subscription.getMomoTransId()),
                    "Hoàn tiền đơn đăng ký bị từ chối");
            if (resp != null && resp.getResultCode() == 0) {
                subscription.setPaymentStatus(MomoPaymentStatus.REFUNDED);
                subscription.setStatus(PartnerSubscriptionStatus.PENDING);
                subscription.setRefundOrderId(refundOrderId);
                subscription.setRefundedAt(LocalDateTime.now());
            } else {
                String msg = resp != null ? resp.getMessage() : "null response";
                log.error("[MoMo] Refund FAILED: subscriptionId={}, msg={}",
                        subscription.getId(), msg);
            }
        } catch (Exception e) {
            log.error("[MoMo] Exception khi refund subscriptionId={}: {}",
                    subscription.getId(), e.getMessage(), e);
        }
    }

    private void createPartnerSubAccount(PartnerSubscription subscription) {
        User owner = subscription.getPartner();
        String shopEmail = subscription.getShopEmail();

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