package org.sep490.backend.module.gamification.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.gamification.dto.request.RewardTransactionRequest;
import org.sep490.backend.module.gamification.dto.response.RewardTransactionResponse;
import org.sep490.backend.module.gamification.entity.RewardTransaction;
import org.sep490.backend.module.gamification.mapper.RewardTransactionMapper;
import org.sep490.backend.module.gamification.repository.RewardTransactionRepository;
import org.sep490.backend.module.gamification.service.RewardTransactionService;
import org.sep490.backend.module.notification.entity.enumeration.NotificationType;
import org.sep490.backend.module.notification.service.FcmService;
import org.sep490.backend.module.partner.dto.filter.VoucherFilter;
import org.sep490.backend.module.user.entity.Level;
import org.sep490.backend.module.user.entity.LevelProgress;
import org.sep490.backend.module.user.entity.enumeration.LevelStatus;
import org.sep490.backend.module.user.repository.LevelProgressRepository;
import org.sep490.backend.module.user.repository.LevelRepository;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RewardTransactionServiceImpl implements RewardTransactionService {

    UserService userService;
    UserRepository userRepository;
    RewardTransactionRepository rewardTransactionRepository;
    RewardTransactionMapper rewardTransactionMapper;
    LevelRepository levelRepository;
    LevelProgressRepository levelProgressRepository;
    FcmService fcmService;

    @Override
    @Transactional(readOnly = true)
    public Page<RewardTransactionResponse> getMyRewardHistory(VoucherFilter filter) {
        User currentUser = userService.getCurrentUser();

        String sortBy = filter.getSortBy().equals("id") ? "createdAt" : filter.getSortBy();
        Sort sort = filter.getSortDir().equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Page<RewardTransaction> histories = rewardTransactionRepository.findByUser_UserId(currentUser.getUserId(), pageable);
        return histories.map(rewardTransactionMapper::toResponse);
    }

    @Override
    @Transactional
    public void createRewardTransaction(RewardTransactionRequest request) {
        User user = userService.getUserById(request.getUserId());

        long pointsChange = request.getPointsAmount() != null ? request.getPointsAmount() : 0L;
        long xpChange = request.getXpAmount() != null ? request.getXpAmount() : 0L;

        long newPoints = user.getTotalPoints() + pointsChange;
        long newXp = user.getTotalXp() + xpChange;

        // Update points and XP
        user.setTotalPoints((int) newPoints);
        user.setTotalXp((int) newXp);

        // Check and update Level when XP changes
        if (xpChange != 0) {
            Level currentLevel = user.getLevel();
            Level newLevel = levelRepository.findFirstByStatusAndRequiredXpLessThanEqualOrderByRequiredXpDesc(
                    LevelStatus.ACTIVE, (int) newXp
            ).orElse(null);

            if (newLevel != null && (currentLevel == null || !currentLevel.getLevelId().equals(newLevel.getLevelId()))) {
                user.setLevel(newLevel);
                if (!levelProgressRepository.existsByUser_UserIdAndLevel_LevelId(user.getUserId(), newLevel.getLevelId())) {
                    LevelProgress levelProgress = LevelProgress.builder()
                            .user(user)
                            .level(newLevel)
                            .xpAtUnlock((int) newXp)
                            .unlockedAt(LocalDateTime.now())
                            .build();
                    levelProgress = levelProgressRepository.save(levelProgress);
                    // push noti if level up
                    fcmService.sendPushNotification(
                            user.getFcmToken(),
                            "Chúc mừng! Bạn đã đạt cấp độ mới: " + newLevel.getName(),
                            "Bạn đã đạt cấp độ " + newLevel.getName() + ". Hãy tiếp tục khám phá để nhận thêm phần thưởng!",
                            NotificationType.LEVEL_UP,
                            levelProgress.getLevelProgressId()
                    );
                }
            }
        }

        userRepository.save(user);

        RewardTransaction transaction = rewardTransactionMapper.toEntity(request, user);
        transaction.setUser(user);
        transaction.setPointsAmount(pointsChange);
        transaction.setXpAmount(xpChange);
        transaction.setPointsBalance(newPoints);
        transaction.setXpBalance(newXp);

        rewardTransactionRepository.save(transaction);
    }
}
