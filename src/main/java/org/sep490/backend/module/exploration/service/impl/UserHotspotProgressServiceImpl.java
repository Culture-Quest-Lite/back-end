package org.sep490.backend.module.exploration.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.locationtech.jts.geom.Point;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.utils.SpatialUtils;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.exploration.dto.request.UserHotspotProgressRequest;
import org.sep490.backend.module.exploration.dto.response.UserHotspotProgressResponse;
import org.sep490.backend.module.exploration.entity.UserHotspotProgress;
import org.sep490.backend.module.exploration.event.CheckInCompletedEvent;
import org.sep490.backend.module.exploration.repository.UserHotspotProgressRepository;
import org.sep490.backend.module.exploration.service.inter.UserHotspotProgressService;
import org.sep490.backend.module.gamification.entity.enumeration.TransactionType;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserHotspotProgressServiceImpl implements UserHotspotProgressService {

    UserHotspotProgressRepository userHotspotProgressRepository;
    HotspotService hotspotService;
    UserService userService;
    ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public UserHotspotProgressResponse checkIn(UserHotspotProgressRequest request) {

        User user = userService.getCurrentUser();
        Hotspot hotspot = hotspotService.getById(request.getHotspotId());

        if (userHotspotProgressRepository.existsByUser_UserIdAndHotspot_HotspotId(user.getUserId(), hotspot.getHotspotId())) {
            throw new BusinessException("Bạn đã check-in tại hotspot này trước đó");
        }

        Point hotspotLocation = hotspot.getLocation();
        Point userLocation = SpatialUtils.fromCoordinates(request.getLongitude(), request.getLatitude());

        double distance = SpatialUtils.calculateDistanceInMeters(hotspotLocation, userLocation);

        if (distance > 50.0) {
            throw new BusinessException("Bạn đang ở ngoài vùng check-in. Hãy di chuyển vào bán kính 50m để check-in");
        }

        UserHotspotProgress progress = UserHotspotProgress.builder()
                .user(user)
                .hotspot(hotspot)
                .location(userLocation)
                .totalPointEarned(hotspot.getPoint().intValue())
                .totalXpEarned(hotspot.getXp().intValue())
                .firstVisitedAt(LocalDateTime.now())
                .build();

        userHotspotProgressRepository.save(progress);

        // Publish event to update route progress and create reward transaction
        eventPublisher.publishEvent(new CheckInCompletedEvent(
                user.getUserId(),
                hotspot.getPoint(),
                hotspot.getXp(),
                hotspot.getHotspotId(),
                hotspot.getHotspotId(),
                TransactionType.HOTSPOT_CHECKIN,
                "Check-in tại hotspot: " + hotspot.getHotspotName()
        ));

        return UserHotspotProgressResponse.builder()
                .userProgressId(progress.getUserProgressId())
                .userId(user.getUserId())
                .hotspotId(hotspot.getHotspotId())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .totalPointEarned(progress.getTotalPointEarned())
                .totalXpEarned(progress.getTotalXpEarned())
                .firstVisitedAt(progress.getFirstVisitedAt())
                .build();
    }
}
