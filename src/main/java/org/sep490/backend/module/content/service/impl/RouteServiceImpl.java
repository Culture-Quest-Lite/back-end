package org.sep490.backend.module.content.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.filter.dto.SearchRequest;
import org.sep490.backend.common.filter.specification.GenericSpecification;
import org.sep490.backend.common.utils.SpatialUtils;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.dto.request.RouteRequest;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.content.dto.response.MediaResponse;
import org.sep490.backend.module.content.dto.response.RouteResponse;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.dto.response.TagResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.entity.enumeration.MediaTargetType;
import org.sep490.backend.module.content.entity.enumeration.RouteDifficulty;
import org.sep490.backend.module.content.entity.enumeration.RouteStatus;
import org.sep490.backend.module.content.entity.enumeration.RouteType;
import org.sep490.backend.module.content.mapper.HotspotMapper;
import org.sep490.backend.module.content.mapper.MediaMapper;
import org.sep490.backend.module.content.mapper.RouteMapper;
import org.sep490.backend.module.content.mapper.StoryMapper;
import org.sep490.backend.module.content.repository.RouteRepository;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.content.service.inter.MediaService;
import org.sep490.backend.module.content.service.inter.RouteService;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.content.repository.TagRepository;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RouteServiceImpl implements RouteService {

    RouteRepository routeRepository;
    RouteMapper routeMapper;
    StoryRepository storyRepository;
    HotspotService hotspotService;
    UserService userService;
    MediaService mediaService;
    MediaMapper mediaMapper;
    StoryMapper storyMapper;
    TagRepository tagRepository;
    HotspotMapper hotspotMapper;

    @Override
    @Transactional
    public RouteResponse create(RouteRequest request) {

        if (request.getHotspotIds().size() < 4) {
            throw new BusinessException("Tuyến đường phải có ít nhất 4 điểm dừng (Hotspot)");
        }

        Tag tag = tagRepository.findById(request.getTagId())
                .orElseThrow(() -> new BusinessException("Tag không tồn tại với ID: " + request.getTagId()));

        Route route = routeMapper.toEntity(request);
        User creator = userService.getCurrentUser();

        route.setCreatedBy(creator);
        route.setTag(tag);
        route.setStatus(RouteStatus.DRAFT);
        route.setType(RouteType.OFFICIAL);
        route.setIsLocked(false);
        route.setTotalStops(request.getHotspotIds().size());

        route = routeRepository.save(route);

        List<Story> stories = processRouteStories(route, request.getHotspotIds());

        RouteResponse response = buildRouteResponse(route, stories);
        if (request.getFiles() != null && request.getFiles().length > 0) {
            try {
                List<MediaResponse> mediaResponses = mediaService.uploadAndSaveMedias(
                        request.getFiles(), MediaTargetType.ROUTE, route.getRouteId());
                response.setMedias(mediaResponses);
            } catch (IOException e) {
                throw new BusinessException("Lỗi tải lên media: " + e.getMessage());
            }
        }
        return response;
    }

    @Override
    @Transactional
    public RouteResponse update(Long id, RouteRequest request) {

        if (request.getHotspotIds().size() < 4) {
            throw new BusinessException("Tuyến đường phải có ít nhất 4 điểm dừng");
        }

        Tag tag = tagRepository.findById(request.getTagId())
                .orElseThrow(() -> new BusinessException("Tag không tồn tại với ID: " + request.getTagId()));

        Route currRoute = getById(id);
        routeMapper.updateFromRequest(currRoute, request);
        currRoute.setTag(tag);
        currRoute.setTotalStops(request.getHotspotIds().size());
        currRoute = routeRepository.save(currRoute);

        // Unset route_id cho tất cả story cũ đang thuộc route này
        List<Story> oldStories = storyRepository.findByRoute_RouteIdOrderByOrderIndexAsc(id);
        for (Story s : oldStories) {
            s.setRoute(null);
            s.setOrderIndex(null);
            s.setDistanceToNext(null);
        }
        storyRepository.saveAll(oldStories);

        List<Story> updatedStories = processRouteStories(currRoute, request.getHotspotIds());
        return buildRouteResponse(currRoute, updatedStories);
    }

    @Override
    @Transactional(readOnly = true)
    public RouteResponse getDetail(Long id) {

        Route route = getById(id);
        List<Story> stories = storyRepository.findByRoute_RouteIdOrderByOrderIndexAsc(id);

        return buildRouteResponse(route, stories);
    }

    @Override
    @Transactional
    public void delete(Long id) {

        Route route = getById(id);
        route.setStatus(RouteStatus.DELETED);
        routeRepository.save(route);
    }

    @Override
    @Transactional(readOnly = true)
    public Route getById(Long id) {

        return routeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Tuyến đường không tồn tại"));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RouteResponse> filterRoutes(SearchRequest request) {

        Sort sort = Sort.by(Sort.Direction.fromString(request.getSortDirection()), request.getSortBy());
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);

        GenericSpecification<Route> spec = new GenericSpecification<>(request);

        return routeRepository.findAll(spec, pageable).map(route -> {
            List<Story> stories = storyRepository.findByRoute_RouteIdOrderByOrderIndexAsc(route.getRouteId());
            return buildRouteResponse(route, stories);
        });
    }

    @Override
    @Transactional
    public RouteResponse addHotspotToEndOfRoute(Long routeId, Long hotspotId) {

        Route route = getById(routeId);
        Hotspot hotspot = hotspotService.getById(hotspotId);
        addHotspotToRoute(route, hotspot);
        List<Story> newStories = storyRepository.findByRoute_RouteId(routeId);

        return buildRouteResponse(route, newStories);
    }

    @Override
    @Transactional
    public RouteResponse removeHotspotFromRoute(Long routeId, Long hotspotId) {
        Route route = getById(routeId);
        Hotspot hotspot = hotspotService.getById(hotspotId);

        if (route.getTotalStops() <= 4) {
            throw new BusinessException("Tuyến đường hiện có 4 điểm dừng, thêm điểm dừng mới trước khi xóa");
        }

        removeHotspotFromRoute(route, hotspot);
        List<Story> newStories = storyRepository.findByRoute_RouteId(routeId);

        return buildRouteResponse(route, newStories);
    }

    @Override
    @Transactional
    public RouteResponse recordJourney() {

        User creator = userService.getCurrentUser();

        if (findRecordingCustomRouteByUserId(creator.getUserId()) != null) {
            throw new BusinessException("Người dùng đã có hành trình đang ghi lại. " +
                    "Vui lòng hoàn thành hành trình trước khi bắt đầu hành trình mới.");
        }

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
        route.setIsLocked(false);
        route.setTotalStops(0);

        route = routeRepository.save(route);

        return buildRouteResponse(route, new ArrayList<>());
    }

    @Override
    @Transactional
    public RouteResponse finishRecordJourney() {

        User user = userService.getCurrentUser();
        Route route = findRecordingCustomRouteByUserId(user.getUserId());

        route.setStatus(RouteStatus.TRIAL);
        route = routeRepository.save(route);

        List<Story> stories = storyRepository.findByRoute_RouteId(route.getRouteId());

        return buildRouteResponse(route, stories);
    }

    @Override
    public Route findRecordingCustomRouteByUserId(Long userId) {

        User user = userService.getUserById(userId);

        return routeRepository
                .findByCreatedByAndTypeAndStatus(user, RouteType.CUSTOM, RouteStatus.RECORDING)
                .orElseThrow(() -> new BusinessException("Không tìm thấy hành trình đang ghi lại của người dùng này."));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteResponse> getByHotspotId(Long hotspotId, RouteStatus routeStatus) {
        Hotspot hotspot = hotspotService.getById(hotspotId);
        List<Route> routes = routeRepository.findRoutesByHotspotIdAndStatus(hotspot.getHotspotId(), routeStatus);
        List<RouteResponse> routeResponses = new ArrayList<>();
        for (Route route : routes) {
            RouteResponse routeResponse = buildRouteResponse(route,
                    storyRepository.findByRoute_RouteIdOrderByOrderIndexAsc(route.getRouteId()));
            routeResponses.add(routeResponse);
        }
        return routeResponses;
    }

    private List<Story> processRouteStories(Route route, List<Long> hotspotIds) {

        if (hotspotIds == null || hotspotIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<Story> stories = new ArrayList<>();

        for (int i = 0; i < hotspotIds.size(); i++) {
            Long hotspotId = hotspotIds.get(i);

            Hotspot hotspot = hotspotService.getById(hotspotId);

            List<Story> hotspotStories = storyRepository.findByHotspotOrderedByIndex(hotspotId);
            if (hotspotStories.isEmpty()) {
                throw new BusinessException("Địa điểm " + hotspot.getHotspotName() + " chưa có cốt truyện nào!");
            }

            Story story = hotspotStories.stream()
                    .filter(s -> s.getTag() != null && route.getTag() != null && s.getTag().getTagId().equals(route.getTag().getTagId()))
                    .findFirst()
                    .orElse(hotspotStories.get(0)); 
            story.setRoute(route);
            story.setOrderIndex(i + 1);

            stories.add(story);
        }

        for (int i = 0; i < stories.size(); i++) {
            Story story = stories.get(i);
            if (i < stories.size() - 1) {
                Story nextStory = stories.get(i + 1);
                double distanceKm = SpatialUtils.calculateDistanceInMeters(
                        story.getHotspot().getLocation(),
                        nextStory.getHotspot().getLocation()) / 1000.0;
                story.setDistanceToNext(distanceKm);
            } else {
                story.setDistanceToNext(0.0);
            }
        }

        return storyRepository.saveAll(stories);
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

    private void addHotspotToRoute(Route route, Hotspot hotspot) {
        List<Story> stories = storyRepository.findByRoute_RouteIdOrderByOrderIndexAsc(route.getRouteId());

        for (Story s : stories) {
            if (s.getHotspot().getHotspotId().equals(hotspot.getHotspotId())) {
                throw new BusinessException("Hotspot này đã có trong tuyến đường");
            }
        }

        List<Story> hotspotStories = storyRepository.findByHotspotOrderedByIndex(hotspot.getHotspotId())
                .stream().filter(s -> s.getRoute() == null).toList();

        Story storyToAdd = hotspotStories.stream()
                .filter(s -> s.getTag() != null && route.getTag() != null && s.getTag().getTagId().equals(route.getTag().getTagId()))
                .findFirst()
                .orElseGet(() -> hotspotStories.stream().findFirst().orElse(null));

        if (storyToAdd == null) {
            throw new BusinessException("Hotspot chưa có story nào. Vui lòng tạo story cho hotspot trước.");
        }

        storyToAdd.setRoute(route);

        if (!stories.isEmpty()) {
            Story lastStory = stories.get(stories.size() - 1);
            storyToAdd.setOrderIndex(lastStory.getOrderIndex() + 1);

            double distanceKm = SpatialUtils.calculateDistanceInMeters(
                    lastStory.getHotspot().getLocation(),
                    hotspot.getLocation()
            ) / 1000.0;
            lastStory.setDistanceToNext(distanceKm);
            storyRepository.save(lastStory);

            route.setTotalDistance(route.getTotalDistance() + distanceKm);
            routeRepository.save(route);
        } else {
            storyToAdd.setOrderIndex(1);
        }
        storyToAdd.setDistanceToNext(0.0);
        storyRepository.save(storyToAdd);
    }

    private void removeHotspotFromRoute(Route route, Hotspot hotspot) {
        List<Story> stories = storyRepository.findByRoute_RouteIdOrderByOrderIndexAsc(route.getRouteId());

        int targetIndex = -1;
        Story targetStory = null;

        for (int i = 0; i < stories.size(); i++) {
            if (stories.get(i).getHotspot().getHotspotId().equals(hotspot.getHotspotId())) {
                targetIndex = i;
                targetStory = stories.get(i);
                break;
            }
        }

        if (targetStory == null) {
            throw new BusinessException("Điểm dừng này không nằm trong tuyến đường");
        }

        if (targetIndex > 0) {
            Story prevStory = stories.get(targetIndex - 1);

            if (targetIndex < stories.size() - 1) {
                Story nextStory = stories.get(targetIndex + 1);

                double newDistanceKm = SpatialUtils.calculateDistanceInMeters(
                        prevStory.getHotspot().getLocation(),
                        nextStory.getHotspot().getLocation()
                ) / 1000.0;
                prevStory.setDistanceToNext(newDistanceKm);
            } else {
                prevStory.setDistanceToNext(0.0);
            }
            storyRepository.save(prevStory);
        }

        for (int i = targetIndex + 1; i < stories.size(); i++) {
            Story nextStory = stories.get(i);
            nextStory.setOrderIndex(nextStory.getOrderIndex() - 1);
            storyRepository.save(nextStory);
        }

        // Unset route (không xóa story, chỉ gỡ khỏi route)
        targetStory.setRoute(null);
        targetStory.setOrderIndex(null);
        targetStory.setDistanceToNext(null);
        storyRepository.save(targetStory);
    }
}