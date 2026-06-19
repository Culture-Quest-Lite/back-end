package org.sep490.backend.module.notification.service;

import org.sep490.backend.module.notification.entity.enumeration.NotificationType;

public interface FcmService {
    void sendPushNotification(String fcmToken, String title, String body,
                              NotificationType type, Long referenceId);
}
