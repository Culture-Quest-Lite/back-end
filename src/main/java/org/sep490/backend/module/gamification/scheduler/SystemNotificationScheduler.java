package org.sep490.backend.module.gamification.scheduler;

import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.notification.entity.enumeration.NotificationType;
import org.sep490.backend.module.notification.service.NotificationService;
import org.sep490.backend.module.partner.entity.VoucherUsage;
import org.sep490.backend.module.partner.repository.VoucherUsageRepository;
import org.sep490.backend.module.social.entity.enumeration.PostActionType;
import org.sep490.backend.module.social.entity.enumeration.PostStatus;
import org.sep490.backend.module.social.repository.PostActionRepository;
import org.sep490.backend.module.social.repository.PostRepository;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SystemNotificationScheduler {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostActionRepository postActionRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final NotificationService notificationService;

    // 9AM daily
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Ho_Chi_Minh")
    public void reportSummaryForAdmin() {
        long reportCount = postActionRepository.findByActionTypeAndIsReportResolved(PostActionType.REPORT, false).size();
        if (reportCount > 0) {
            List<User> admins = userRepository.findByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE);
            notificationService.sendToMultipleUsers(
                    admins,
                    "Tóm tắt báo cáo hàng ngày",
                    "Hệ thống hiện có " + reportCount + " báo cáo bài viết chờ xử lý.",
                    NotificationType.POST,
                    null
            );
        }
    }

    // 9AM daily
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Ho_Chi_Minh")
    public void pendingPostReminderForAdmin() {
        long pendingCount = postRepository.countByStatus(PostStatus.PENDING);
        if (pendingCount >= 10) {
            List<User> admins = userRepository.findByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE);
            notificationService.sendToMultipleUsers(
                    admins,
                    "Nhắc nhở duyệt bài",
                    "Hiện có " + pendingCount + " bài viết đang chờ phê duyệt.",
                    NotificationType.POST,
                    null
            );
        }
    }

    // 7 days before voucher's expiration
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Ho_Chi_Minh")
    public void voucherExpiryReminder() {
        LocalDateTime start = LocalDateTime.now().plusDays(7).with(LocalTime.MIN);
        LocalDateTime end = LocalDateTime.now().plusDays(7).with(LocalTime.MAX);
        List<VoucherUsage> expiringVouchers = voucherUsageRepository.findByExpiredAtBetweenAndIsUsedFalse(start, end);
        for (VoucherUsage vu : expiringVouchers) {
            notificationService.sendAndSave(
                    vu.getUser(),
                    "Voucher sắp hết hạn",
                    "Voucher '" + vu.getVoucher().getVoucherName() + "' sẽ hết hạn sau 7 ngày nữa.",
                    NotificationType.REDEEM_VOUCHER,
                    vu.getVoucherUsageId()
            );
        }
    }

    // Voucher usage report
    @Scheduled(cron = "0 0 20 * * *", zone = "Asia/Ho_Chi_Minh")
    public void partnerVoucherDailyReport() {
        List<User> partners = userRepository.findByRoleAndStatus(UserRole.PARTNER, UserStatus.ACTIVE);
        notificationService.sendToMultipleUsers(
                partners,
                "Báo cáo Voucher hàng ngày",
                "Báo cáo lượt sử dụng voucher của bạn đã sẵn sàng.",
                NotificationType.REDEEM_VOUCHER,
                null
        );
    }
}
