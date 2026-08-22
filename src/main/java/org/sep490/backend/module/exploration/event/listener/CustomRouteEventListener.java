package org.sep490.backend.module.exploration.event.listener;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.enumeration.ContentType;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.content.service.inter.RouteService;
import org.sep490.backend.module.exploration.event.CheckInCompletedEvent;
import org.sep490.backend.module.notification.entity.enumeration.NotificationType;
import org.sep490.backend.module.notification.service.NotificationService;
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
    NotificationService notificationService;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCheckInCompleted(CheckInCompletedEvent event) {

        Hotspot hotspot = hotspotService.getById(event.hotspotId());
        User user = userService.getUserById(event.userId());
        Route customRoute = routeService.findRecordingCustomRouteByUserId(user.getUserId());

        if (hotspot.getContentType().equals(ContentType.TEMP)) {
            notificationService.sendAndSave(
                user,
                "Check-in địa điểm tạm thời",
                "Địa điểm bạn vừa check-in là địa điểm tạm thời, sẽ không còn tồn tại tới ngày " + hotspot.getValidTo(),
                NotificationType.USER,
                hotspot.getHotspotId()
            );
        } else {
            routeService.addHotspotToEndOfCustomRoute(customRoute.getRouteId(), hotspot.getHotspotId(), user.getUserId());

        }
    }
}
