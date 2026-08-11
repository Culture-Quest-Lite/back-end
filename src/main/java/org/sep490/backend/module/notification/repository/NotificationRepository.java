package org.sep490.backend.module.notification.repository;

import org.sep490.backend.module.notification.entity.Notification;
import org.sep490.backend.module.notification.entity.enumeration.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByUser_UserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    long countByUser_UserIdAndIsReadFalse(Long userId);
    Optional<Notification> findFirstByUser_UserIdAndNotificationTypeAndReferenceIdAndIsReadFalseOrderByCreatedAtDesc(
            Long userId, NotificationType type, Long referenceId);

    List<Notification> findByIsReadAndCreatedAtBefore(Boolean isRead, LocalDateTime thresholdDate);

    List<Notification> findByIsReadAndReadAtBefore(Boolean isRead, LocalDateTime readAtBefore);
}
