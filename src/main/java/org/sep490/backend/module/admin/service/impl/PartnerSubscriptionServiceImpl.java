package org.sep490.backend.module.admin.service.impl;

import jakarta.mail.internet.MimeMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.config.keycloak.KeyCloakAuthClient;
import org.sep490.backend.module.admin.dto.request.PartnerSubscriptionRequest;
import org.sep490.backend.module.admin.dto.response.PartnerSubscriptionResponse;
import org.sep490.backend.module.admin.entity.PartnerSubscription;
import org.sep490.backend.module.admin.entity.SubscriptionPlan;
import org.sep490.backend.module.admin.entity.enumeration.BillingCycleEnum;
import org.sep490.backend.module.admin.entity.enumeration.PartnerSubscriptionStatus;
import org.sep490.backend.module.admin.mapper.PartnerSubscriptionMapper;
import org.sep490.backend.module.admin.repository.PartnerSubscriptionRepository;
import org.sep490.backend.module.admin.repository.SubscriptionPlanRepository;
import org.sep490.backend.module.admin.service.PartnerSubscriptionService;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.content.dto.response.MediaResponse;
import org.sep490.backend.module.content.enums.MediaTargetType;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PartnerSubscriptionServiceImpl implements PartnerSubscriptionService {
    SubscriptionPlanRepository planRepository;
    PartnerSubscriptionRepository partnerSubscriptionRepository;
    PartnerSubscriptionMapper subscriptionMapper;
    UserService userService;
    KeyCloakAuthClient keyCloakAuthClient;
    UserRepository userRepository;
    S3Service s3Service;
    MediaService mediaService;

    private final JavaMailSender mailSender;
    @Value("${app.frontend-url:${FRONTEND_URL:http://localhost:3000}}")
    @NonFinal
    String frontendUrl;

    @Override
    @Transactional
    public PartnerSubscriptionResponse registerSubscription(PartnerSubscriptionRequest request) {
        User currentPartner = userService.getCurrentUser();
        SubscriptionPlan plan = planRepository.findById(request.getSubscriptionPlanId())
                .orElseThrow(() -> new BusinessException("Gói đăng ký không tồn tại"));

//        if (!partnerSubscriptionRepository.isLocationInVietnam(request.getLongitude(), request.getLatitude())) {
//            throw new BusinessException("Vị trí của shop phải nằm trong lãnh thổ Việt Nam");
//        }

        if (userRepository.existsByEmail(request.getShopEmail())) {
            throw new BusinessException("Email quản lý shop này đã được đăng ký cho một tài khoản khác.");
        }

        PartnerSubscription subscription = subscriptionMapper.toEntity(request);
        subscription.setPartner(currentPartner);
        subscription.setSubscriptionPlan(plan);
        subscription.setIsVerified(false);
        subscription.setStatus(PartnerSubscriptionStatus.PENDING);
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
                throw new BusinessException("Không thể duyệt: Email shop cung cấp đã bị trùng lặp với người dùng khác trong hệ thống.");
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
                    List.of("PARTNER")
            );

            User partnerAccount = User.builder()
                    .keycloakUserId(keycloakUserId)
                    .username(partnerUsername)
                    .email(shopEmail)
                    .displayName(owner.getDisplayName())
                    .status(UserStatus.ACTIVE)
                    .role(UserRole.PARTNER)
                    .build();
            userRepository.save(partnerAccount);
            sendPartnerCredentialsEmail(owner.getEmail(), shopEmail, partnerUsername, rawPassword, owner.getDisplayName());
        } catch (Exception e) {
            throw new BusinessException("Lỗi hệ thống khi tạo tài khoản quản lý cho Partner: " + e.getMessage());
        }
    }

    private void sendPartnerCredentialsEmail(String ownerEmail, String shopEmail, String username, String password, String ownerName) throws Exception {
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