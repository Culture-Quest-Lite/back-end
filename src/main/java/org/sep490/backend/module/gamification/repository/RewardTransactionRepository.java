package org.sep490.backend.module.gamification.repository;

import org.sep490.backend.module.gamification.entity.RewardTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RewardTransactionRepository extends JpaRepository<RewardTransaction, Long> {
    Page<RewardTransaction> findByUser_UserId(Long userId, Pageable pageable);
    Optional<RewardTransaction> findFirstByUser_UserIdOrderByCreatedAtDesc(Long userId);
}
