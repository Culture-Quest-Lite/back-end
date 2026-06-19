package org.sep490.backend.module.gamification.repository;

import org.sep490.backend.module.gamification.entity.PointTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {
    Page<PointTransaction> findByUser_UserId(Long userId, Pageable pageable);
}
