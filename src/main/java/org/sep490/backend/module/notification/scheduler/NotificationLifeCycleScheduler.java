package org.sep490.backend.module.notification.scheduler;

import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.notification.service.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationLifeCycleScheduler {
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 0 * * *") // Run daily at midnight
    public void updateUnReadNotifications() {
        notificationService.updateUnReadNotification();
    }

    @Scheduled(cron = "0 0 0 * * *") // Run daily at midnight
    public void deleteReadNotifications() {
        notificationService.deleteReadNotification();
    }
}
