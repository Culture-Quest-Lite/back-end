package org.sep490.backend.module.partner.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.module.admin.entity.Invoice;
import org.sep490.backend.module.admin.entity.enumeration.InvoiceStatus;
import org.sep490.backend.module.admin.entity.enumeration.PlanType;
import org.sep490.backend.module.admin.repository.InvoiceRepository;
import org.sep490.backend.module.admin.repository.PartnerInfoRepository;
import org.sep490.backend.module.admin.entity.enumeration.PartnerInfoStatus;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authorization.service.EntitlementCacheService;
import org.sep490.backend.module.notification.entity.enumeration.NotificationType;
import org.sep490.backend.module.notification.service.NotificationService;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PartnerExpiryScheduler {

    private final InvoiceRepository invoiceRepository;
    private final UserService userService;
    private final PartnerInfoRepository partnerInfoRepository;
    private final NotificationService notificationService;
    private final EntitlementCacheService entitlementCacheService;

    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void expirePartnerInvoices() {
        LocalDateTime now = LocalDateTime.now();

        List<Invoice> expired = invoiceRepository.findExpiredInvoicesByPlanType(
                InvoiceStatus.ACTIVE, now, PlanType.PARTNER);

        for (Invoice inv : expired) {
            inv.setStatus(InvoiceStatus.EXPIRED);
            User user = inv.getUser();

            boolean stillActive = invoiceRepository.existsActiveInvoiceForUserAndPlanType(
                    user.getUserId(), InvoiceStatus.ACTIVE, now, PlanType.PARTNER);

            if (!stillActive) {
                try {
                    if (user.getRole() == UserRole.PARTNER) {
                        userService.updateUserRole(user.getUserId(), UserRole.EXPLORER);
                    }

                    partnerInfoRepository.findByUser_UserId(user.getUserId()).ifPresent(partnerInfo -> {
                        partnerInfo.setStatus(PartnerInfoStatus.INACTIVE);
                        partnerInfoRepository.save(partnerInfo);
                        log.info("[PartnerExpiry] Đã đóng băng gian hàng của user {}", user.getUserId());
                    });

                    entitlementCacheService.evict(user.getUserId());

                    notificationService.sendAndSave(
                            user,
                            "Gói Partner đã hết hạn",
                            "Gói đăng ký Partner của bạn đã hết hạn. Quyền lợi hệ thống và gian hàng của bạn hiện đã bị tạm ngưng. Vui lòng gia hạn để tiếp tục.",
                            NotificationType.SUBSCRIPTION,
                            inv.getInvoiceId()
                    );
                } catch (Exception e) {
                    log.error("[PartnerExpiry] Lỗi khi tước quyền Partner của user {}: {}", user.getUserId(), e.getMessage());
                }
            }
        }

        invoiceRepository.saveAll(expired);

        if (!expired.isEmpty()) {
            log.info("[PartnerExpiry] Đã xử lý hết hạn {} hóa đơn Partner", expired.size());
        }
    }
}