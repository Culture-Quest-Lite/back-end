package org.sep490.backend.module.notification.service.impl;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.module.notification.entity.enumeration.NotificationType;
import org.sep490.backend.module.notification.service.FcmService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmServiceImpl implements FcmService {


    @Override
    public void sendPushNotification(String fcmToken, String title, String message,
                                     NotificationType type, Long referenceId) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.debug("FCM token is null or blank, skipping push notification.");
            return;
        }

        try {
            Map<String, String> data = new HashMap<>();
            data.put("type", type != null ? type.name() : "");
            data.put("referenceId", referenceId != null ? referenceId.toString() : "");

            Message fcmMessage = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(message)
                            .build())
                    .putAllData(data)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().send(fcmMessage);
            log.info("Push notification sent successfully. Message ID: {}", response);

        } catch (FirebaseMessagingException e) {
            log.error("Failed to send push notification via FCM. Token: {}, Error: {}",
                    fcmToken, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error while sending push notification: {}", e.getMessage(), e);
        }
    }
}
