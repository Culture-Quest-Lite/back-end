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

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

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
        notificationRepository.save(notification);
        evictUnreadCount(userId);
    }

    @Override
    public long countUnread(Long userId) {
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
}
