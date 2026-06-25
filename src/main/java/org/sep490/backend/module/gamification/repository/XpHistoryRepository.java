package org.sep490.backend.module.gamification.repository;

import org.sep490.backend.module.gamification.entity.XpHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface XpHistoryRepository extends JpaRepository<XpHistory, Long> {
    Optional<XpHistory> findFirstByUser_UserIdOrderByCreatedAtDesc(Long userId);
}
