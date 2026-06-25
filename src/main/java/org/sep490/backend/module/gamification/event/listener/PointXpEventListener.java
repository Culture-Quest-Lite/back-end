package org.sep490.backend.module.gamification.event.listener;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.gamification.dto.request.PointTransactionRequest;
import org.sep490.backend.module.gamification.dto.request.XpHistoryRequest;
import org.sep490.backend.module.gamification.entity.XpHistory;
import org.sep490.backend.module.gamification.event.PointXpUpdatedEvent;
import org.sep490.backend.module.gamification.service.PointTransactionService;
import org.sep490.backend.module.gamification.service.XpHistoryService;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PointXpEventListener {

    UserService userService;
    HotspotService hotspotService;
    PointTransactionService pointTransactionService;
    XpHistoryService xpHistoryService;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCheckInCompleted(PointXpUpdatedEvent event) {

        User user = userService.getUserById(event.getUserId());
        Long currUserPoint = Long.valueOf(user.getTotalPoints());

        Hotspot hotspot = hotspotService.getById(event.getHotspotId());
        Long earnedPoint = hotspot.getPoint();
        Long earnedXp =  hotspot.getXp();

        Long newUserPoint = currUserPoint + earnedPoint;

        // Point Transaction
        PointTransactionRequest pointTransactionRequest = PointTransactionRequest.builder()
                .userId(event.getUserId())
                .pointAmount(earnedPoint)
                .hotspotId(event.getHotspotId())
                .transactionType(event.getTransactionType())
                .description(event.getDescription())
                .referenceId(event.getReferenceId())
                .balanceRemaining(newUserPoint)
                .build();
        pointTransactionService.createPointTransaction(pointTransactionRequest);

        // XP Transaction
        XpHistoryRequest request = XpHistoryRequest.builder()
                .userId(event.getUserId())
                .xpAmount(earnedXp)
                .source(event.getSource())
                .referenceId(event.getReferenceId())
                .description(event.getDescription())
                .build();
        xpHistoryService.create(request);
    }
}
