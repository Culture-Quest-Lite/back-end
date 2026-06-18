package org.sep490.backend.module.exploration.event.listener;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.entity.RouteHotspot;
import org.sep490.backend.module.content.repository.RouteHotspotRepository;
import org.sep490.backend.module.exploration.entity.UserRouteProgress;
import org.sep490.backend.module.exploration.entity.enumuration.ProgressStatus;
import org.sep490.backend.module.exploration.event.CheckInCompletedEvent;
import org.sep490.backend.module.exploration.repository.UserRouteProgressRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RouteProgressEventListener {

    RouteHotspotRepository routeHotspotRepository;
    UserRouteProgressRepository userRouteProgressRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCheckInCompleted(CheckInCompletedEvent event) {
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
            }
        }

        if (!activeProgresses.isEmpty()) {
            userRouteProgressRepository.saveAll(activeProgresses);
        }
    }
}
