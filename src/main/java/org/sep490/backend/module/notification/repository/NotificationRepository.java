package org.sep490.backend.module.notification.repository;

import org.sep490.backend.module.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByUser_UserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    long countByUser_UserIdAndIsReadFalse(Long userId);
}
