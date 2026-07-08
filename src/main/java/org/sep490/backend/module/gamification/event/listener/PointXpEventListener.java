package org.sep490.backend.module.gamification.event.listener;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.content.service.inter.RouteService;
import org.sep490.backend.module.exploration.event.CheckInCompletedEvent;
import org.sep490.backend.module.exploration.event.RouteProgressCompletedEvent;
import org.sep490.backend.module.gamification.dto.request.RewardTransactionRequest;
import org.sep490.backend.module.gamification.entity.enumeration.TransactionType;
import org.sep490.backend.module.gamification.service.RewardTransactionService;
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
    RewardTransactionService rewardTransactionService;
    RouteService routeService;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCheckInCompleted(CheckInCompletedEvent event) {

        User user = userService.getUserById(event.userId());
        Long currUserPoint = Long.valueOf(user.getTotalPoints());

        Hotspot hotspot = hotspotService.getById(event.hotspotId());
        Long earnedPoint = hotspot.getPoint();
        Long earnedXp =  hotspot.getXp();

        // Reward Transaction
        RewardTransactionRequest rewardRequest = RewardTransactionRequest.builder()
                .userId(event.userId())
                .pointsAmount(earnedPoint)
                .xpAmount(earnedXp)
                .transactionType(event.transactionType())
                .description(event.description())
                .referenceId(event.referenceId())
                .build();
        rewardTransactionService.createRewardTransaction(rewardRequest);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRouteProgressCompleted(RouteProgressCompletedEvent event) {
        User user = userService.getUserById(event.userId());

        Route route = routeService.getById(event.routeId());
        Long earnedPoint = route.getPoint();
        Long earnedXp =  route.getXp();

        // Reward Transaction
        RewardTransactionRequest rewardRequest = RewardTransactionRequest.builder()
                .userId(event.userId())
                .pointsAmount(earnedPoint)
                .xpAmount(earnedXp)
                .transactionType(TransactionType.ROUTE_COMPLETION)
                .description("User #" + user.getUserId() + " completed route #" + route.getRouteId() + " and earned " + earnedPoint + " points.")
                .referenceId(route.getRouteId())
                .build();
        rewardTransactionService.createRewardTransaction(rewardRequest);
    }
}
