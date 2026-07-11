package org.sep490.backend.module.exploration.event.listener;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.RouteRepository;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.exploration.entity.RouteParticipant;
import org.sep490.backend.module.exploration.entity.enumuration.ProgressStatus;
import org.sep490.backend.module.exploration.event.CheckInCompletedEvent;
import org.sep490.backend.module.exploration.event.RouteProgressCompletedEvent;
import org.sep490.backend.module.exploration.repository.RouteParticipantRepository;
import org.springframework.context.ApplicationEventPublisher;
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

    StoryRepository storyRepository;
    RouteParticipantRepository routeParticipantRepository;
    HotspotRepository hotspotRepository;
    ApplicationEventPublisher eventPublisher;
    RouteRepository routeRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCheckInCompleted(CheckInCompletedEvent event) {

        Hotspot hotspot = hotspotRepository.findById(event.hotspotId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hotspot với ID: " + event.hotspotId()));

        List<Long> routeIds = storyRepository.findRouteIdsByHotspot_HotspotId(event.hotspotId());

        if (routeIds.isEmpty()) {
            return;
        }

        List<RouteParticipant> unfinishedProgresses = routeParticipantRepository
                .findByUser_UserIdAndRoute_RouteIdInAndStatusNot(event.userId(), routeIds, ProgressStatus.COMPLETED);

        for (RouteParticipant progress : unfinishedProgresses) {
            int newCompletedStops = progress.getCompletedStops() + 1;
            progress.setCompletedStops(newCompletedStops);

            double newPercentage = ((double) newCompletedStops / progress.getTotalStops()) * 100;
            progress.setProgressPercentage(Math.min(newPercentage, 100.0));

            if (newCompletedStops >= progress.getTotalStops()) {
                progress.setStatus(ProgressStatus.COMPLETED);
                progress.setCompletedAt(LocalDateTime.now());
                eventPublisher.publishEvent(new RouteProgressCompletedEvent(
                        event.userId(),
                        progress.getRoute().getRouteId()
                ));

                Route route = progress.getRoute();
                route.setTotalCheckIns(route.getTotalCheckIns() + 1);
                routeRepository.save(progress.getRoute());
            }
        }

        if (!unfinishedProgresses.isEmpty()) {
            routeParticipantRepository.saveAll(unfinishedProgresses);
        }
    }
}
