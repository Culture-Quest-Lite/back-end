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
import org.sep490.backend.module.exploration.dto.request.CheckInRequest;
import org.sep490.backend.module.exploration.dto.response.CheckInResponse;
import org.sep490.backend.module.exploration.entity.CheckIn;
import org.sep490.backend.module.exploration.event.CheckInCompletedEvent;
import org.sep490.backend.module.exploration.mapper.CheckInMapper;
import org.sep490.backend.module.exploration.repository.CheckInRepository;
import org.sep490.backend.module.exploration.service.inter.CheckInService;
import org.sep490.backend.module.gamification.entity.enumeration.TransactionType;
import org.sep490.backend.module.gamification.entity.enumeration.ActionType;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE,  makeFinal = true)
public class CheckInServiceImpl implements CheckInService {

    CheckInRepository checkInRepository;
    HotspotService hotspotService;
    UserService userService;
    CheckInMapper checkInMapper;
    ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CheckInResponse checkIn(CheckInRequest checkInRequest) {

        User user = userService.getCurrentUser();
        Hotspot hotspot = hotspotService.getById(checkInRequest.getHotspotId());

        if(checkInRepository.existsByUser_UserIdAndHotspot_HotspotId(user.getUserId(), hotspot.getHotspotId())) {
            throw new BusinessException("Bạn đã check-in tại hotspot này trước đó");
        }

        Point hotspotLocation = hotspot.getLocation();
        Point userLocation = SpatialUtils.fromCoordinates(checkInRequest.getLongitude(), checkInRequest.getLatitude());

        double distance = SpatialUtils.calculateDistanceInMeters(hotspotLocation, userLocation);

        if(distance > 50l) {
            throw new BusinessException("Bạn đang ở ngoài vùng check-in. Hãy di chuyển vào bán kính 50m để check-in");
        }

        CheckIn checkin = checkInMapper.toEntity(checkInRequest, user, hotspot, null, distance);
        checkin.setHotspot(hotspot);
        checkInRepository.save(checkin);

        // use to update UserRouteProgress
        // update user point, xp after check-in
        eventPublisher.publishEvent(new CheckInCompletedEvent(
                user.getUserId(),
                hotspot.getPoint(),
                hotspot.getXp(),
                hotspot.getHotspotId(),
                hotspot.getHotspotId(),
                TransactionType.HOTSPOT_CHECKIN,
                "Check-in tại hotspot: " + hotspot.getHotspotName(),
                ActionType.HOTSPOT_CHECKIN
        ));
        return checkInMapper.toResponse(checkin);
    }
}
