package org.sep490.backend.module.notification.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.config.redis.CacheNames;
import org.sep490.backend.config.redis.RedisCircuitBreaker;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.notification.dto.response.NotificationResponse;
import org.sep490.backend.module.notification.entity.Notification;
import org.sep490.backend.module.notification.entity.enumeration.NotificationType;
import org.sep490.backend.module.notification.mapper.NotificationMapper;
import org.sep490.backend.module.notification.repository.NotificationRepository;
import org.sep490.backend.module.notification.service.FcmService;
import org.sep490.backend.module.notification.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /** Trần kích thước trang cho danh sách thông báo. */
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final FcmService fcmService;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisCircuitBreaker circuitBreaker;

    @Override
    @Transactional
    public void sendAndSave(User user, String title, String message,
            NotificationType type, Long referenceId) {
        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .notificationType(type)
                .referenceId(referenceId)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
        notificationRepository.save(notification);
        evictUnreadCount(user.getUserId());
        log.debug("Saved notification to DB for userId={}, type={}", user.getUserId(), type);

        fcmService.sendPushNotification(user.getFcmToken(), title, message, type, referenceId);
    }

    @Override
    @Transactional
    public void updateDeviceToken(Long userId, String token) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User không tồn tại"));
        user.setFcmToken(token);
        userRepository.save(user);
    }

    @Override
    public Page<NotificationResponse> getMyNotification(Long userId, Pageable pageable) {
        if (userId == null) {
            throw new BusinessException("Không xác định được người dùng");
        }
        if (pageable == null) {
            throw new BusinessException("Thiếu thông tin phân trang");
        }
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new BusinessException("Kích thước trang không được vượt quá 100");
        }
        return notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(userId, pageable)
                .map(notificationMapper::toResponse);
    }

    @Override
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException("Thông báo không tồn tại"));

        if (!notification.getUser().getUserId().equals(userId)) {
            throw new BusinessException("Bạn không có quyền sở hữu thông báo này");
        }

        notification.setIsRead(true);
        notification.setReadAt(LocalDateTime.now());
        notificationRepository.save(notification);
        evictUnreadCount(userId);
    }

    @Override
    public long countUnread(Long userId) {
        if (userId == null) {
            throw new BusinessException("Không xác định được người dùng");
        }
        String key = CacheNames.KEY_NOTIF_UNREAD + userId;

        Object cached = circuitBreaker.read("notif.unread.get",
                () -> redisTemplate.opsForValue().get(key), null);
        if (cached != null) {
            return Long.parseLong(cached.toString());
        }

        long count = notificationRepository.countByUser_UserIdAndIsReadFalse(userId);
        circuitBreaker.write("notif.unread.set",
                () -> redisTemplate.opsForValue().set(key, String.valueOf(count), Duration.ofMinutes(5)));
        return count;
    }

    private void evictUnreadCount(Long userId) {
        if (userId == null) {
            return;
        }
        circuitBreaker.write("notif.unread.evict",
                () -> redisTemplate.delete(CacheNames.KEY_NOTIF_UNREAD + userId));
    }

    @Override
    @Transactional
    public void sendToMultipleUsers(
            List<User> users,
            String title,
            String message,
            NotificationType type,
            Long referenceId) {
        // 1. Tạo danh sách các thông báo riêng biệt cho từng user
        List<Notification> notifications = users.stream()
                .map(user -> Notification.builder()
                        .user(user)
                        .title(title)
                        .message(message)
                        .notificationType(type)
                        .referenceId(referenceId)
                        .isRead(false)
                        .createdAt(LocalDateTime.now())
                        .build())
                .toList();

        // 2. Lưu toàn bộ xuống DB bằng batch insert
        notificationRepository.saveAll(notifications);

        // 3. Gửi push notification qua FCM cho từng user (nếu có)
        for (User user : users) {
            fcmService.sendPushNotification(user.getFcmToken(), title, message, type, referenceId);
        }
    }

    @Override
    @Transactional
    public void sendOrUpdateInteractionNotification(User sender, User receiver, Long postId, long totalInteractions) {
        if (sender.getUserId().equals(receiver.getUserId())) {
            return;
        }

        Optional<Notification> existingNoti = notificationRepository
                .findFirstByUser_UserIdAndNotificationTypeAndReferenceIdAndIsReadFalseOrderByCreatedAtDesc(
                        receiver.getUserId(), NotificationType.POST, postId);

        String title = "Tương tác mới trên bài viết";
        String message = totalInteractions <= 1
                ? sender.getDisplayName() + " đã tương tác với bài viết của bạn."
                : sender.getDisplayName() + " và " + (totalInteractions - 1) + " người khác đã tương tác với bài viết của bạn.";

        if (existingNoti.isPresent()) {
            Notification noti = existingNoti.get();
            noti.setMessage(message);
            noti.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(noti);
        } else {
            sendAndSave(receiver, title, message, NotificationType.POST, postId);
        }
    }

    @Override
    @Transactional
    public void updateUnReadNotification() {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<Notification> notifications = notificationRepository.findByIsReadAndCreatedAtBefore(false, thirtyDaysAgo);
        for (Notification notification : notifications) {
            notification.setIsRead(true);
        }
        notificationRepository.saveAll(notifications);
    }

    @Override
    @Transactional
    public void deleteReadNotification() {
        LocalDateTime ninetyDaysAgo = LocalDateTime.now().minusDays(90);
        List<Notification> notifications = notificationRepository.findByIsReadAndReadAtBefore(true, ninetyDaysAgo);
        notificationRepository.deleteAll(notifications);
    }
}
