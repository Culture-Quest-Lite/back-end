package org.sep490.backend.module.exploration.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.dto.request.FinalizeCustomRouteRequest;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.content.dto.response.RouteResponse;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.content.entity.enumeration.RouteDifficulty;
import org.sep490.backend.module.content.entity.enumeration.RouteStatus;
import org.sep490.backend.module.content.entity.enumeration.RouteType;
import org.sep490.backend.module.content.mapper.HotspotMapper;
import org.sep490.backend.module.content.mapper.RouteMapper;
import org.sep490.backend.module.content.mapper.StoryMapper;
import org.sep490.backend.module.content.repository.RouteRepository;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.content.repository.TagRepository;
import org.sep490.backend.module.content.service.inter.RouteService;
import org.sep490.backend.module.exploration.service.inter.CustomRouteService;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CustomRouteServiceImpl implements CustomRouteService {

    UserService userService;
    RouteService routeService;
    RouteRepository routeRepository;
    TagRepository tagRepository;
    StoryRepository storyRepository;
    HotspotMapper hotspotMapper;
    StoryMapper storyMapper;
    RouteMapper routeMapper;

    @Override
    @Transactional
    public RouteResponse recordJourney() {

        User creator = userService.getCurrentUser();

        if (routeRepository.findByCreatedByAndTypeAndStatus(creator, RouteType.CUSTOM, RouteStatus.RECORDING).orElse(null) != null) {
            throw new BusinessException("Người dùng đã có hành trình đang ghi lại. " +
                    "Vui lòng hoàn thành hành trình trước khi bắt đầu hành trình mới.");
        }

        // default tag for custom route
        Tag tag = tagRepository.findByTagName("Hành Trình Cá Nhân")
                .orElseThrow(() -> new BusinessException("Không tìm thấy tag 'Hành trình cá nhân'"));

        Route route = new Route();

        int createdRoutes = routeRepository.countByCreatedBy(creator);

        route.setRouteName("Hành trình #" + (createdRoutes + 1) + " của " + creator.getDisplayName());
        route.setDescription("Hành trình #" + (createdRoutes + 1) + " của " + creator.getDisplayName());
        route.setDifficulty(RouteDifficulty.EASY);
        route.setXp(0L);
        route.setPoint(0L);
        route.setEstimateTime(0.0);
        route.setTotalDistance(0.0);
        route.setCreatedBy(creator);
        route.setStatus(RouteStatus.RECORDING);
        route.setType(RouteType.CUSTOM);
        route.setTag(tag);
        route.setIsLocked(false);
        route.setTotalStops(0);

        route = routeRepository.save(route);

        return buildRouteResponse(route, new ArrayList<>());
    }

    @Override
    @Transactional
    public RouteResponse finishRecordJourney() {

        User user = userService.getCurrentUser();
        Route route = routeService.findRecordingCustomRouteByUserId(user.getUserId());

        if(route.getStories().size() < 4) {
            throw new BusinessException("Hành trình cá nhân phải có ít nhất 4 điểm dừng (Hotspot)");
        }

        route.setStatus(RouteStatus.DRAFT); // wait for user to finalize their custom route
        route = routeRepository.save(route);

        List<Story> stories = route.getStories();

        return buildRouteResponse(route, stories);
    }

    @Override
    @Transactional
    public RouteResponse finalizeCustomRoute(FinalizeCustomRouteRequest request) {

        Route route = routeService.getById(request.getRouteId());
        User user = userService.getCurrentUser();

        List<Story> stories = route.getStories();

        int totalStops = stories.size();
        long totalDistance;
        long estimateTime;

        if(totalStops < 4) {
            throw new BusinessException("Hành trình cá nhân phải có ít nhất 4 điểm dừng (Hotspot) để hoàn tất");
        }

        if(!route.getStatus().equals(RouteStatus.DRAFT)) {
            throw new BusinessException("Chỉ có thể hoàn tất hành trình cá nhân đang ở trạng thái DRAFT");
        }

        if(!route.getType().equals(RouteType.CUSTOM)) {
            throw new BusinessException("Chỉ có thể hoàn tất hành trình cá nhân có loại CUSTOM");
        }

        if(!route.getCreatedBy().equals(user)) {
            throw new BusinessException("Người dùng chỉ được hoàn thành hành trình cá nhân của mình");
        }

        route.setStatus(RouteStatus.PUBLISHED);
        route.setDescription(request.getDescription());
        route.setTotalStops(totalStops);
        route.setXp(500L);
        route.setPoint(500L);
        route = routeRepository.save(route);

        return buildRouteResponse(route, route.getStories());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteResponse> getMyJourney(RouteStatus routeStatus) {

        User user = userService.getCurrentUser();
        List<Route> routes;

        if (routeStatus == null) {
            routes = routeRepository.findAllByCreatedByAndType(user, RouteType.CUSTOM);
        } else {
            routes = routeRepository.findAllByCreatedByAndTypeAndStatus(user, RouteType.CUSTOM, routeStatus);
        }

        if (routes.isEmpty()) {
            throw new BusinessException("Không tìm thấy hành trình cá nhân nào"
                    + (routeStatus != null ? " với trạng thái: " + routeStatus : ""));
        }

        return routes.stream()
                .map(route -> buildRouteResponse(route, route.getStories()))
                .toList();
    }

    private RouteResponse buildRouteResponse(Route route, List<Story> stories) {

        RouteResponse response = routeMapper.toResponse(route);

        List<HotspotResponse> hotspotResponses = new ArrayList<>();

        for (Story s : stories) {
            if (s.getHotspot() != null) {
                HotspotResponse hr = buildHotspotResponseForRoute(s.getHotspot(), route);
                hotspotResponses.add(hr);
            }
        }

        response.setHotspots(hotspotResponses);

        if (route.getTag() != null) {
            response.setTag(storyMapper.toTagResponse(route.getTag()));
        }

        return response;
    }

    private HotspotResponse buildHotspotResponseForRoute(Hotspot hotspot, Route route) {
        HotspotResponse response = hotspotMapper.toResponse(hotspot);

        List<Story> stories;
        if (route.getTag() != null) {
            List<Long> routeTagIds = List.of(route.getTag().getTagId());
            stories = storyRepository.findByHotspotOrderedByRouteTag(hotspot.getHotspotId(), routeTagIds);
        } else {
            stories = storyRepository.findByHotspotOrderedByIndex(hotspot.getHotspotId());
        }

        List<StoryResponse> storyResponses = stories.stream()
                .map(storyMapper::toResponse)
                .toList();
        response.setStories(storyResponses);

        return response;
    }
}
