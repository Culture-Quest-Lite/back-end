package org.sep490.backend.module.exploration.service.impl;

import org.sep490.backend.module.content.service.inter.CheckInStatusService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.locationtech.jts.geom.Point;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.utils.SpatialUtils;
import org.sep490.backend.module.admin.service.CheckInRadiusConfigService;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.exploration.dto.request.UserHotspotProgressRequest;
import org.sep490.backend.module.exploration.dto.response.CheckInEligibilityResponse;
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
    HotspotRepository hotspotRepository;
    UserService userService;
    ApplicationEventPublisher eventPublisher;
    CheckInStatusService checkInStatusService;
    CheckInRadiusConfigService checkInRadiusConfigService;

    @Override
    @Transactional
    public UserHotspotProgressResponse checkIn(UserHotspotProgressRequest request) {

        User user = userService.getCurrentUser();
        Hotspot hotspot = hotspotService.getById(request.getHotspotId());

        if (userHotspotProgressRepository.existsByUser_UserIdAndHotspot_HotspotId(user.getUserId(), hotspot.getHotspotId())) {
            throw new BusinessException("Bạn đã check-in tại hotspot này trước đó");
        }

        Point userLocation = SpatialUtils.fromCoordinates(request.getLongitude(), request.getLatitude());

        double tolerance = CheckInPolicy.toleranceFrom(request.getAccuracy());
        CheckInEvaluation evaluation = evaluate(hotspot, request.getLatitude(), request.getLongitude(), tolerance);

        if (!evaluation.eligible()) {
            throw new BusinessException(buildOutOfZoneMessage(evaluation));
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
        checkInStatusService.addCheckedIn(user.getUserId(), hotspot.getHotspotId());

        eventPublisher.publishEvent(new CheckInCompletedEvent(
                user.getUserId(),
                hotspot.getHotspotId(),
                progress.getUserProgressId(),
                TransactionType.HOTSPOT_CHECKIN,
                "Check-in tại hotspot: " + hotspot.getHotspotName()
        ));

        return UserHotspotProgressResponse.builder()
                .userProgressId(progress.getUserProgressId())
                .userId(user.getUserId())
                .hotspotId(hotspot.getHotspotId())
                .isCheckedIn(true)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .totalPointEarned(progress.getTotalPointEarned())
                .totalXpEarned(progress.getTotalXpEarned())
                .firstVisitedAt(progress.getFirstVisitedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CheckInEligibilityResponse checkEligibility(Long hotspotId, Double latitude,
                                                       Double longitude, Double accuracy) {
        User user = userService.getCurrentUser();
        Hotspot hotspot = hotspotService.getById(hotspotId);

        boolean alreadyCheckedIn = userHotspotProgressRepository
                .existsByUser_UserIdAndHotspot_HotspotId(user.getUserId(), hotspot.getHotspotId());

        double tolerance = CheckInPolicy.toleranceFrom(accuracy);
        CheckInEvaluation evaluation = evaluate(hotspot, latitude, longitude, tolerance);

        String message;
        if (alreadyCheckedIn) {
            message = "Bạn đã check-in tại hotspot này trước đó";
        } else if (evaluation.eligible()) {
            message = "Bạn đã đến nơi, có thể check-in";
        } else {
            message = buildOutOfZoneMessage(evaluation);
        }

        return CheckInEligibilityResponse.builder()
                .eligible(evaluation.eligible() && !alreadyCheckedIn)
                .distanceMeters(evaluation.distanceMeters())
                .requiredMeters(evaluation.requiredMeters())
                .alreadyCheckedIn(alreadyCheckedIn)
                .message(message)
                .build();
    }

    private record CheckInEvaluation(boolean eligible, double distanceMeters, double requiredMeters) {
    }

    private CheckInEvaluation evaluate(Hotspot hotspot, Double latitude, Double longitude, double tolerance) {

        if (hotspot.getBoundary() != null) {
            Boolean within = hotspotRepository.isWithinBoundary(
                    hotspot.getHotspotId(), longitude, latitude, tolerance);

            if (within != null) {
                Double distanceToEdge = hotspotRepository.distanceToBoundary(
                        hotspot.getHotspotId(), longitude, latitude);
                return new CheckInEvaluation(
                        within,
                        distanceToEdge != null ? distanceToEdge : 0.0,
                        tolerance);
            }
        }

        Point hotspotLocation = hotspot.getLocation();
        if (hotspotLocation == null) {
            throw new BusinessException("Hotspot chưa có toạ độ hợp lệ");
        }

        Point userLocation = SpatialUtils.fromCoordinates(longitude, latitude);
        double distance = SpatialUtils.calculateDistanceInMeters(hotspotLocation, userLocation);
        double required = CheckInPolicy.effectiveRadius(
                hotspot.getCheckInRadius(), checkInRadiusConfigService.getDefaultRadius()) + tolerance;

        return new CheckInEvaluation(distance <= required, distance, required);
    }

    private String buildOutOfZoneMessage(CheckInEvaluation evaluation) {
        return String.format("Bạn đang cách %.0fm, cần vào trong phạm vi %.0fm để check-in",
                evaluation.distanceMeters(), evaluation.requiredMeters());
    }
}
