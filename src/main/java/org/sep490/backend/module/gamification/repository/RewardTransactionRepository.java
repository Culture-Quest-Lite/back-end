package org.sep490.backend.module.gamification.repository;

import org.sep490.backend.module.gamification.dto.projection.LeaderboardProjection;
import org.sep490.backend.module.gamification.entity.RewardTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RewardTransactionRepository extends JpaRepository<RewardTransaction, Long> {
    Page<RewardTransaction> findByUser_UserId(Long userId, Pageable pageable);
    Optional<RewardTransaction> findFirstByUser_UserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT r.user.userId AS userId, SUM(r.xpAmount) AS totalXp " +
            "FROM RewardTransaction r " +
            "WHERE r.createdAt >= :startDate AND r.createdAt < :endDate " +
            "AND r.transactionType != TransactionType.WEEKLY_LEADERBOARD_BONUS " +
            "GROUP BY r.user.userId " +
            "ORDER BY SUM(r.xpAmount) DESC")
    List<LeaderboardProjection> findTopUsersInDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
}
