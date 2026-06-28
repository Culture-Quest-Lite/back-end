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
import org.sep490.backend.module.gamification.dto.request.PointTransactionRequest;
import org.sep490.backend.module.gamification.dto.request.XpHistoryRequest;
import org.sep490.backend.module.gamification.entity.enumeration.ActionType;
import org.sep490.backend.module.gamification.entity.enumeration.TransactionType;
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

        Long newUserPoint = currUserPoint + earnedPoint;

        // Point Transaction
        PointTransactionRequest pointTransactionRequest = PointTransactionRequest.builder()
                .userId(event.userId())
                .pointAmount(earnedPoint)
                .transactionType(event.transactionType())
                .description(event.description())
                .referenceId(event.referenceId())
                .balanceRemaining(newUserPoint)
                .build();
        pointTransactionService.createPointTransaction(pointTransactionRequest);

        // XP Transaction
        XpHistoryRequest request = XpHistoryRequest.builder()
                .userId(event.userId())
                .xpAmount(earnedXp)
                .source(event.source())
                .referenceId(event.referenceId())
                .description(event.description())
                .build();
        xpHistoryService.create(request);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRouteProgressCompleted(RouteProgressCompletedEvent event) {
        User user = userService.getUserById(event.userId());
        Long currUserPoint = Long.valueOf(user.getTotalPoints());
        //

        Route route = routeService.getById(event.routeId());
        Long earnedPoint = route.getPoint();
        Long earnedXp =  route.getXp();

        Long newUserPoint = currUserPoint + earnedPoint;

        // Point Transaction
        PointTransactionRequest pointTransactionRequest = PointTransactionRequest.builder()
                .userId(event.userId())
                .pointAmount(earnedPoint)
                .transactionType(TransactionType.ROUTE_COMPLETION)
                .description("User #" + user.getUserId() + " completed route #" + route.getRouteId() + " and earned " + earnedPoint + " points.")
                .referenceId(route.getRouteId())
                .balanceRemaining(newUserPoint)
                .build();
        pointTransactionService.createPointTransaction(pointTransactionRequest);

        // XP Transaction
        XpHistoryRequest request = XpHistoryRequest.builder()
                .userId(event.userId())
                .xpAmount(earnedXp)
                .source(ActionType.ROUTE_COMPLETION)
                .referenceId(route.getRouteId())
                .description("User #" + user.getUserId() + " completed route #" + route.getRouteId() + " and earned " + earnedXp + " XP.")
                .build();
        xpHistoryService.create(request);
    }
}
