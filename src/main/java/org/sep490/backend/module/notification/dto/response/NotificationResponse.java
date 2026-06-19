package org.sep490.backend.module.notification.dto.response;

import lombok.Data;
import org.sep490.backend.module.notification.entity.enumeration.NotificationType;

import java.time.LocalDateTime;

@Data
public class NotificationResponse {
    private Long id;
    private String title;
    private String message;
    private boolean isRead;
    private LocalDateTime createdAt;
    private NotificationType notificationType;
    private Long referenceId;
}
