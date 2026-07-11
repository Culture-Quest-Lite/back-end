package org.sep490.backend.module.exploration.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.content.service.inter.RouteService;
import org.sep490.backend.module.exploration.dto.filter.RouteParticipantFilter;
import org.sep490.backend.module.exploration.dto.response.HotspotProgressInRouteResponse;
import org.sep490.backend.module.exploration.dto.response.RouteParticipantDetailResponse;
import org.sep490.backend.module.exploration.dto.response.RouteParticipantResponse;
import org.sep490.backend.module.exploration.entity.RouteParticipant;
import org.sep490.backend.module.exploration.entity.enumuration.ProgressStatus;
import org.sep490.backend.module.exploration.event.RouteProgressCompletedEvent;
import org.sep490.backend.module.exploration.mapper.RouteParticipantMapper;
import org.sep490.backend.module.exploration.repository.RouteParticipantRepository;
import org.sep490.backend.module.exploration.repository.UserHotspotProgressRepository;
import org.sep490.backend.module.exploration.service.inter.RouteParticipantService;
import org.sep490.backend.module.exploration.specification.RouteParticipantSpecification;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RouteParticipantServiceImpl implements RouteParticipantService {

    RouteParticipantRepository routeParticipantRepository;
    UserHotspotProgressRepository userHotspotProgressRepository;
    StoryRepository storyRepository;
    UserService userService;
    RouteService routeService;
    RouteParticipantMapper routeParticipantMapper;
    ApplicationEventPublisher eventPublisher;

    @Override
    public HashMap<Integer, RouteParticipantResponse> startRouteProgress(Long routeId) {
        User user = userService.getCurrentUser();
        Route route = routeService.getById(routeId);
        RouteParticipant participant = routeParticipantRepository
                .findByRoute_RouteIdAndUser_UserId(route.getRouteId(), user.getUserId()).orElse(null);
        HashMap<Integer, RouteParticipantResponse> result = new HashMap<>();

        if (participant == null) {
            List<Hotspot> routeHotspots = storyRepository.findHotspotsByRouteIdOrderByIndexAsc(route.getRouteId());
            int completedStops = 0;
            for (Hotspot h : routeHotspots) {
                if (userHotspotProgressRepository.existsByUser_UserIdAndHotspot_HotspotId(user.getUserId(), h.getHotspotId())) {
                    completedStops++;
                }
            }

            int totalStops = routeHotspots.size();
            double progressPercentage = totalStops > 0 ? ((double) completedStops / totalStops) * 100.0 : 0.0;

            participant = RouteParticipant.builder()
                    .route(route)
                    .user(user)
                    .totalStops(totalStops)
                    .completedStops(completedStops)
                    .progressPercentage(Math.min(progressPercentage, 100.0))
                    .status(ProgressStatus.IN_PROGRESS)
                    .build();

            if (completedStops >= totalStops && totalStops > 0) {
                participant.setStatus(ProgressStatus.COMPLETED);
                participant.setCompletedAt(LocalDateTime.now());
                participant = routeParticipantRepository.save(participant);

                eventPublisher.publishEvent(new RouteProgressCompletedEvent(
                        user.getUserId(),
                        route.getRouteId()
                ));
            } else {
                participant = routeParticipantRepository.save(participant);
            }

            result.put(201, routeParticipantMapper.toResponse(participant));
        } else {
            if (participant.getStatus() == ProgressStatus.IN_PROGRESS) {
                throw new BusinessException("Bạn hiện đang trong tuyến đường này");
            } else {
                List<Hotspot> routeHotspots = storyRepository.findHotspotsByRouteIdOrderByIndexAsc(route.getRouteId());
                int completedStops = 0;
                for (Hotspot h : routeHotspots) {
                    if (userHotspotProgressRepository.existsByUser_UserIdAndHotspot_HotspotId(user.getUserId(), h.getHotspotId())) {
                        completedStops++;
                    }
                }
                int totalStops = routeHotspots.size();
                double progressPercentage = totalStops > 0 ? ((double) completedStops / totalStops) * 100.0 : 0.0;

                participant.setCompletedStops(completedStops);
                participant.setProgressPercentage(Math.min(progressPercentage, 100.0));
                participant.setStatus(ProgressStatus.IN_PROGRESS);

                if (completedStops >= totalStops && totalStops > 0) {
                    participant.setStatus(ProgressStatus.COMPLETED);
                    participant.setCompletedAt(LocalDateTime.now());
                    participant = routeParticipantRepository.save(participant);

                    eventPublisher.publishEvent(new RouteProgressCompletedEvent(
                            user.getUserId(),
                            route.getRouteId()
                    ));
                } else {
                    participant = routeParticipantRepository.save(participant);
                }
                result.put(200, routeParticipantMapper.toResponse(participant));
            }
        }

        return result;
    }

    @Override
    public RouteParticipantResponse abandonRouteProgress(Long routeId) {
        User user = userService.getCurrentUser();
        Route route = routeService.getById(routeId);
        RouteParticipant participant = routeParticipantRepository
                .findByRoute_RouteIdAndUser_UserId(route.getRouteId(), user.getUserId()).orElse(null);

        if (participant == null) {
            throw new BusinessException("Bạn chưa bắt đầu hành trình " + route.getRouteName());
        } else {
            if (participant.getStatus() == ProgressStatus.ABANDONED) {
                throw new BusinessException("Bạn đã kết thúc tuyến đường này");
            } else {
                participant.setStatus(ProgressStatus.ABANDONED);
                participant = routeParticipantRepository.save(participant);
            }
        }

        return routeParticipantMapper.toResponse(participant);
    }

    @Override
    public Page<RouteParticipantResponse> getAll(RouteParticipantFilter filter) {
        User user = userService.getCurrentUser();

        Sort sort = filter.getSortDir().equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(filter.getSortBy()).ascending()
                : Sort.by(filter.getSortBy()).descending();
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Specification<RouteParticipant> spec = RouteParticipantSpecification.filterProgress(filter, user);

        return routeParticipantRepository.findAll(spec, pageable).map(routeParticipantMapper::toResponse);
    }

    @Override
    public RouteParticipantDetailResponse getRouteProgress(Long progressId) {
        RouteParticipant participant = getById(progressId);
        Route route = participant.getRoute();
        User progressUser = participant.getUser();
        User currentUser = userService.getCurrentUser();

        if (!progressUser.equals(currentUser)) {
            throw new BusinessException("Bạn chưa bắt đầu tuyến đường này");
        }

        List<HotspotProgressInRouteResponse> hotspots = storyRepository
                .getHotspotCheckInStatusByRouteAndUserNative(route.getRouteId(), currentUser.getUserId())
                .stream()
                .map(p -> HotspotProgressInRouteResponse.builder()
                        .userProgressId(p.getUserProgressId())
                        .userId(p.getUserId())
                        .hotspotId(p.getHotspotId())
                        .isCheckedIn(p.getIsCheckedIn())
                        .index(p.getIndex())
                        .latitude(p.getLatitude())
                        .longitude(p.getLongitude())
                        .totalPointEarned(p.getTotalPointEarned())
                        .totalXpEarned(p.getTotalXpEarned())
                        .firstVisitedAt(p.getFirstVisitedAt())
                        .build()
                )
                .toList();

        RouteParticipantDetailResponse detailResponse = routeParticipantMapper.toDetailResponse(participant);
        detailResponse.setHotspotProgressList(hotspots);

        return detailResponse;
    }

    @Override
    public RouteParticipant getById(Long progressId) {
        return routeParticipantRepository.findById(progressId)
                .orElseThrow(() -> new BusinessException("User route progress not found"));
    }
}
