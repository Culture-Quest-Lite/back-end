package org.sep490.backend.module.gamification.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.module.gamification.dto.projection.LeaderboardProjection;
import org.sep490.backend.module.gamification.dto.request.RewardTransactionRequest;
import org.sep490.backend.module.gamification.entity.enumeration.TransactionType;
import org.sep490.backend.module.gamification.repository.RewardTransactionRepository;
import org.sep490.backend.module.gamification.service.RewardTransactionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WeeklyLeaderboardScheduler {

    private final RewardTransactionRepository rewardTransactionRepository;
    private final RewardTransactionService rewardTransactionService;

    @Scheduled(cron = "0 0 0 * * MON", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void processWeeklyRewards() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfLastWeek = now.minusWeeks(1).with(DayOfWeek.MONDAY).toLocalDate().atStartOfDay();
        LocalDateTime endOfLastWeek = now.with(DayOfWeek.MONDAY).toLocalDate().atStartOfDay();

        List<LeaderboardProjection> topUsers = rewardTransactionRepository.findTopUsersInDateRange(
                startOfLastWeek, endOfLastWeek, PageRequest.of(0, 100));

        if (topUsers.isEmpty()) {
            return;
        }

        int rank = 1;
        for (LeaderboardProjection record : topUsers) {
            long[] bonusXp = calculateBonusXp(rank);

            if (bonusXp[0] > 0) {
                RewardTransactionRequest rewardRequest = RewardTransactionRequest.builder()
                        .userId(record.getUserId())
                        .pointsAmount(bonusXp[1])
                        .xpAmount(bonusXp[0])
                        .transactionType(TransactionType.WEEKLY_LEADERBOARD_BONUS)
                        .description("Thưởng XP xếp hạng " + rank + " Leaderboard tuần")
                        .build();

                rewardTransactionService.createRewardTransaction(rewardRequest);
            }
            rank++;
        }
    }

    private long[] calculateBonusXp(int rank) {
        if (rank == 1) return new long[]{1000, 500};
        if (rank == 2) return new long[]{800, 400};
        if (rank == 3) return new long[]{500, 250};
        if (rank >= 4 && rank <= 10) return new long[]{300, 150};
        if (rank >= 11 && rank <= 50) return new long[]{100, 50};
        if (rank >= 51 && rank <= 100) return new long[]{50, 25};
        return new long[]{0, 0};
    }
}
