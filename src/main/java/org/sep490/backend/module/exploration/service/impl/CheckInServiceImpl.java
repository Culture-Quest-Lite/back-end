package org.sep490.backend.module.exploration.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.locationtech.jts.geom.Point;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.utils.SpatialUtils;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.RouteHotspot;
import org.sep490.backend.module.content.repository.RouteHotspotRepository;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.exploration.dto.request.CheckInRequest;
import org.sep490.backend.module.exploration.dto.response.CheckInResponse;
import org.sep490.backend.module.exploration.entity.CheckIn;
import org.sep490.backend.module.exploration.entity.UserRouteProgress;
import org.sep490.backend.module.exploration.entity.enumuration.ProgressStatus;
import org.sep490.backend.module.exploration.mapper.CheckInMapper;
import org.sep490.backend.module.exploration.repository.CheckInRepository;
import org.sep490.backend.module.exploration.repository.UserRouteProgressRepository;
import org.sep490.backend.module.exploration.service.inter.CheckInService;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE,  makeFinal = true)
public class CheckInServiceImpl implements CheckInService {

    CheckInRepository checkInRepository;
    RouteHotspotRepository routeHotspotRepository;
    UserRouteProgressRepository userRouteProgressRepository;
    HotspotService hotspotService;
    UserService userService;
    CheckInMapper checkInMapper;

    @Override
    @Transactional
    public CheckInResponse checkIn(CheckInRequest checkInRequest) {

        User user = userService.getCurrentUser();
        Hotspot hotspot = hotspotService.getById(checkInRequest.getHotspotId());
        Point hotspotLocation = hotspot.getLocation();
        Point userLocation = SpatialUtils.fromCoordinates(checkInRequest.getLongitude(), checkInRequest.getLatitude());

        double distance = SpatialUtils.calculateDistanceInMeters(hotspotLocation, userLocation);
        if(distance > hotspot.getCheckInRadius()) {
            throw new BusinessException("User is not within the check-in radius of the hotspot");
        }

        CheckIn checkin = checkInMapper.toEntity(checkInRequest, user, hotspot, null, distance);
        checkInRepository.save(checkin);

        return checkInMapper.toResponse(checkin);
    }

    private void updateRelatedRouteProgresses(Long userId, Long hotspotId) {

        List<RouteHotspot> routeHotspots = routeHotspotRepository.findByHotspot_HotspotId(hotspotId);
        if (routeHotspots.isEmpty()) {
            return;
        }

        List<Long> routeIds = routeHotspots.stream()
                .map(rh -> rh.getRoute().getRouteId())
                .toList();

        List<UserRouteProgress> activeProgresses = userRouteProgressRepository
                .findByUser_UserIdAndRoute_RouteIdInAndStatusNot(userId, routeIds, ProgressStatus.COMPLETED);

        for (UserRouteProgress progress : activeProgresses) {
            int newCompletedStops = progress.getCompletedStops() + 1;
            progress.setCompletedStops(newCompletedStops);

            double newPercentage = ((double) newCompletedStops / progress.getTotalStops()) * 100;
            progress.setProgressPercentage(Math.min(newPercentage, 100.0));

            if (newCompletedStops >= progress.getTotalStops()) {
                progress.setStatus(ProgressStatus.COMPLETED);
                progress.setCompletedAt(LocalDateTime.now());

                // TODO: update user's xp and points after complete route
            }
        }

        if (!activeProgresses.isEmpty()) {
            userRouteProgressRepository.saveAll(activeProgresses);
        }
    }
}
