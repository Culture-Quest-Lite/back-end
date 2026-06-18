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
import org.sep490.backend.module.exploration.mapper.CheckInMapper;
import org.sep490.backend.module.exploration.repository.CheckInRepository;
import org.sep490.backend.module.exploration.service.inter.CheckInService;
import org.sep490.backend.module.user.service.UserService;
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

    @Override
    public CheckInResponse checkIn(CheckInRequest checkInRequest) {

        User user = userService.getCurrentUser();
        Hotspot hotspot = hotspotService.getById(checkInRequest.getHotspotId());
        Point hotspotLocation = hotspot.getLocation();
        Point userLocation = SpatialUtils.fromCoordinates(checkInRequest.getLongitude(), checkInRequest.getLatitude());

        double distance = SpatialUtils.calculateDistanceInMeters(hotspotLocation, userLocation);
        if(distance < hotspot.getCheckInRadius()) {
            throw new BusinessException("User is not within the check-in radius of the hotspot");
        }

        CheckIn checkin = checkInMapper.toEntity(checkInRequest, user, hotspot, null, distance);
        checkInRepository.save(checkin);

        return checkInMapper.toResponse(checkin);
    }
}
