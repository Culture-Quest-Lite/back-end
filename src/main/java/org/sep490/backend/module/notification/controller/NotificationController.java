package org.sep490.backend.module.notification.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.notification.dto.request.DeviceTokenRequest;
import org.sep490.backend.module.notification.dto.response.NotificationResponse;
import org.sep490.backend.module.notification.entity.enumeration.NotificationType;
import org.sep490.backend.module.notification.service.NotificationService;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    @PostMapping("/token")
    public ResponseEntity<String> registerDeviceToken(
            @Valid @RequestBody DeviceTokenRequest request
    ) {
        Long currentUserId = userService.getCurrentUser().getUserId();
        notificationService.updateDeviceToken(currentUserId, request.getToken());
        return ResponseEntity.ok("Đã cập nhật device token thành công");
    }

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long currentUserId = userService.getCurrentUser().getUserId();
        return ResponseEntity.ok(notificationService.getMyNotification(currentUserId, pageable));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount() {
        Long currentUserId = userService.getCurrentUser().getUserId();
        return ResponseEntity.ok(notificationService.countUnread(currentUserId));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<String> markAsRead(@PathVariable Long id) {
        Long currentUserId = userService.getCurrentUser().getUserId();
        notificationService.markAsRead(currentUserId, id);
        return ResponseEntity.ok("Đã đánh dấu đã đọc");
    }

    @PostMapping("/test-push")
    public ResponseEntity<String> testPushNotification() {
        var user = userService.getCurrentUser();
        notificationService.sendAndSave(
                user,
                "🔔 Test Notification",
                "Push notification đang hoạt động!",
                NotificationType.EARN,
                null
        );
        return ResponseEntity.ok("Push gửi thành công đến fcmToken: " + user.getFcmToken());
    }
}
