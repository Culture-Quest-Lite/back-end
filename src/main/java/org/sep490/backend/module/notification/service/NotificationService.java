package org.sep490.backend.module.notification.service;

import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.notification.dto.response.NotificationResponse;
import org.sep490.backend.module.notification.entity.enumeration.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    void sendAndSave(User user, String title, String message,
                     NotificationType type, Long referenceId);

    void updateDeviceToken(Long userId, String token);

    Page<NotificationResponse> getMyNotification(Long userId, Pageable pageable);

    void markAsRead(Long userId, Long notificationId);

    long countUnread(Long userId);
}
