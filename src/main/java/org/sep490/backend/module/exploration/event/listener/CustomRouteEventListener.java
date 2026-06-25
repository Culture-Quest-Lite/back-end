package org.sep490.backend.module.exploration.event.listener;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.content.service.inter.RouteService;
import org.sep490.backend.module.exploration.event.CustomRouteUpdatedEvent;
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
public class CustomRouteEventListener {

    HotspotService hotspotService;
    UserService userService;
    RouteService routeService;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCheckInCompleted(CustomRouteUpdatedEvent event) {

        Hotspot hotspot = hotspotService.getById(event.getHotspotId());
        User user = userService.getUserById(event.getUserId());
        Route customRoute = routeService.findRecordingCustomRouteByUserId(user.getUserId());

        routeService.addHotspotToEndOfRoute(customRoute.getRouteId(), hotspot.getHotspotId());
    }
}
