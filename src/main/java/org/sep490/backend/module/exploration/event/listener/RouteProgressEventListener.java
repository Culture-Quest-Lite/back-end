package org.sep490.backend.module.exploration.event.listener;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.RouteHotspot;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.RouteHotspotRepository;
import org.sep490.backend.module.exploration.entity.UserRouteProgress;
import org.sep490.backend.module.exploration.entity.enumuration.ProgressStatus;
import org.sep490.backend.module.exploration.event.RouteProgressUpdatedEvent;
import org.sep490.backend.module.exploration.repository.UserRouteProgressRepository;
import org.sep490.backend.module.gamification.entity.enumeration.TransactionType;
import org.sep490.backend.module.gamification.entity.enumeration.XpSource;
import org.sep490.backend.module.gamification.event.PointXpUpdatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.EventListener;
import java.util.List;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RouteProgressEventListener {

    RouteHotspotRepository routeHotspotRepository;
    UserRouteProgressRepository userRouteProgressRepository;
    HotspotRepository hotspotRepository;
    ApplicationEventPublisher eventPublisher;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCheckInCompleted(RouteProgressUpdatedEvent event) {

        Hotspot hotspot = hotspotRepository.findById(event.getHotspotId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hotspot với ID: " + event.getHotspotId()));

        List<RouteHotspot> routeHotspots = routeHotspotRepository.findByHotspot_HotspotId(event.getHotspotId());
        if (routeHotspots.isEmpty()) {
            return;
        }

        List<Long> routeIds = routeHotspots.stream()
                .map(rh -> rh.getRoute().getRouteId())
                .toList();

        List<UserRouteProgress> activeProgresses = userRouteProgressRepository
                .findByUser_UserIdAndRoute_RouteIdInAndStatusNot(event.getUserId(), routeIds, ProgressStatus.COMPLETED);

        for (UserRouteProgress progress : activeProgresses) {
            int newCompletedStops = progress.getCompletedStops() + 1;
            progress.setCompletedStops(newCompletedStops);

            double newPercentage = ((double) newCompletedStops / progress.getTotalStops()) * 100;
            progress.setProgressPercentage(Math.min(newPercentage, 100.0));

            if (newCompletedStops >= progress.getTotalStops()) {
                progress.setStatus(ProgressStatus.COMPLETED);
                progress.setCompletedAt(LocalDateTime.now());
                // update user point, xp after finish route
                eventPublisher.publishEvent(new PointXpUpdatedEvent(
                        event.getUserId(),
                        progress.getRoute().getPoint(),
                        progress.getRoute().getXp(),
                        event.getHotspotId(),
                        hotspot.getHotspotId(),
                        TransactionType.ROUTE_COMPLETION,
                        "Hoàn thành tuyến đường: " + progress.getRoute().getRouteName(),
                        XpSource.ROUTE_COMPLETION
                ));
            }
        }

        if (!activeProgresses.isEmpty()) {
            userRouteProgressRepository.saveAll(activeProgresses);
        }
    }
}
