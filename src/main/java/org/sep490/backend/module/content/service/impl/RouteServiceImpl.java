package org.sep490.backend.module.content.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.filter.dto.SearchRequest;
import org.sep490.backend.common.filter.specification.GenericSpecification;
import org.sep490.backend.common.utils.SpatialUtils;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.dto.request.RouteHotspotRequest;
import org.sep490.backend.module.content.dto.request.RouteRequest;
import org.sep490.backend.module.content.dto.response.RouteResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.RouteHotspot;
import org.sep490.backend.module.content.enums.ContentStatus;
import org.sep490.backend.module.content.enums.RouteType;
import org.sep490.backend.module.content.mapper.RouteMapper;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.RouteHotspotRepository;
import org.sep490.backend.module.content.repository.RouteRepository;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.content.service.inter.RouteService;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.content.repository.TagRepository;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE,  makeFinal = true)
public class RouteServiceImpl implements RouteService {

    RouteRepository routeRepository;
    RouteMapper routeMapper;
    RouteHotspotRepository routeHotspotRepository;
    HotspotService hotspotService;
    UserService userService;
    TagRepository tagRepository;

    @Override
    @Transactional
    public RouteResponse create(RouteRequest request) {

        if(request.getHotspots().size() < 4) {
            throw new BusinessException("Tuyến đường phải có ít nhất 4 điểm dừng (Hotspot)");
        }

        Route route = routeMapper.toEntity(request);
        List<Tag> tags = tagRepository.findAllById(request.getTagIds());
        route.setTags(new HashSet<>(tags));
        User creator = userService.getCurrentUser();

        route.setCreatedBy(creator);
        route.setStatus(ContentStatus.DRAFT);
        route.setType(RouteType.OFFICIAL);
        route.setIsLocked(false);
        route.setTotalStops(request.getHotspots().size());

        route = routeRepository.save(route);

        List<RouteHotspot> routeHotspots = processRouteHotspots(route, request.getHotspots());

        return buildRouteResponse(route, routeHotspots);
    }

    @Override
    @Transactional
    public RouteResponse update(Long id, RouteRequest request) {

        if(request.getHotspots().size() < 4) {
            throw new BusinessException("Tuyến đường phải có ít nhất 4 điểm dừng (Hotspot)");
        }

        Route currRoute = getById(id);

        routeMapper.updateFromRequest(currRoute, request);
        List<Tag> tags = tagRepository.findAllById(request.getTagIds());
        currRoute.setTags(new HashSet<>(tags));
        currRoute.setTotalStops(request.getHotspots().size());
        currRoute = routeRepository.save(currRoute);

        routeHotspotRepository.deleteByRoute_RouteId(id);

        List<RouteHotspot> updatedRouteHotspot = processRouteHotspots(currRoute, request.getHotspots());

        return buildRouteResponse(currRoute, updatedRouteHotspot);
    }

    @Override
    @Transactional(readOnly = true)
    public RouteResponse getDetail(Long id) {

        Route route  = getById(id);
        List<RouteHotspot> routeHotspots = routeHotspotRepository.findByRoute_RouteIdOrderByIndexAsc(id);

        return buildRouteResponse(route, routeHotspots);
    }

    @Override
    @Transactional
    public void delete(Long id) {

        Route route =  getById(id);

        route.setStatus(ContentStatus.DELETED);
        routeRepository.save(route);
    }

    @Override
    @Transactional(readOnly = true)
    public Route getById(Long id) {

        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Tuyến đường không tồn tại"));

        return route;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RouteResponse> filterRoutes(SearchRequest request) {

        Sort sort = Sort.by(Sort.Direction.fromString(request.getSortDirection()), request.getSortBy());
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);

        GenericSpecification<Route> spec = new GenericSpecification<>(request);

        return routeRepository.findAll(spec, pageable).map(routeMapper::toResponse);
    }

    @Override
    @Transactional
    public RouteResponse addHotspotToEndOfRoute(Long routeId, Long hotspotId) {

        Route route = getById(routeId);
        Hotspot hotspot = hotspotService.getById(hotspotId);
        addHotspotToRoute(route, hotspot);
        List<RouteHotspot> newRouteHotspots = routeHotspotRepository.findByRoute_RouteId(routeId);

        return buildRouteResponse(route, newRouteHotspots);
    }

    @Override
    @Transactional
    public RouteResponse removeHotspotFromRoute(Long routeId, Long hotspotId) {
        Route route = getById(routeId);
        Hotspot hotspot = hotspotService.getById(hotspotId);
        List<RouteHotspot> routeHotspots = routeHotspotRepository.findByRoute_RouteId(routeId);

        if(routeHotspots.size() <= 4) {
            throw new BusinessException("Tuyến đường hiện có 4 điểm dừng, thêm điểm dừng mới trước khi xóa");
        }

        removeHotspotFromRoute(route, hotspot);
        List<RouteHotspot> newRouteHotspots = routeHotspotRepository.findByRoute_RouteId(routeId);

        return buildRouteResponse(route, newRouteHotspots);
    }


    private List<RouteHotspot> processRouteHotspots(Route route, List<RouteHotspotRequest> hotspotRequests) {

        if (hotspotRequests == null || hotspotRequests.isEmpty()) {
            return new ArrayList<>();
        }

        List<RouteHotspot> routeHotspots = new ArrayList<>();

        for (int i = 0; i < hotspotRequests.size(); i++) {

            RouteHotspotRequest req = hotspotRequests.get(i);

            Hotspot hotspot = hotspotService.getById(req.getHotspotId());

            RouteHotspot routeHotspot = RouteHotspot.builder()
                    .route(route)
                    .hotspot(hotspot)
                    .index(req.getIndex() != null ? req.getIndex() : i)
                    .build();

            if (i < hotspotRequests.size() - 1) {

                Long nextHotspotId = hotspotRequests.get(i + 1).getHotspotId();

                Hotspot nextHotspot = hotspotService.getById(nextHotspotId);

                double distanceInMeters = SpatialUtils.calculateDistanceInMeters(
                        hotspot.getLocation(),
                        nextHotspot.getLocation());
                routeHotspot.setDistanceToNext(distanceInMeters);
            } else {
                routeHotspot.setDistanceToNext(0.0);
            }
            routeHotspots.add(routeHotspot);
        }

        return routeHotspotRepository.saveAll(routeHotspots);
    }

    private RouteResponse buildRouteResponse(Route route, List<RouteHotspot> routeHotspots) {

        RouteResponse response = routeMapper.toResponse(route);

        response.setHotspots(routeMapper.toRouteHotspotResponseList(routeHotspots));

        return response;
    }

    private void addHotspotToRoute(Route route, Hotspot hotspot) {
        List<RouteHotspot> routeHotspots = routeHotspotRepository.findByRoute_RouteIdOrderByIndexAsc(route.getRouteId());

        for (RouteHotspot rh : routeHotspots) {
            if (rh.getHotspot().getHotspotId().equals(hotspot.getHotspotId())) {
                throw new BusinessException("Điểm dừng đã tồn tại trong tuyến đường");
            }
        }

        RouteHotspot newRouteHotspot = new RouteHotspot();
        newRouteHotspot.setRoute(route);
        newRouteHotspot.setHotspot(hotspot);
        newRouteHotspot.setDistanceToNext(0.0);

        if (!routeHotspots.isEmpty()) {
            RouteHotspot lastRouteHotspot = routeHotspots.get(routeHotspots.size() - 1);

            newRouteHotspot.setIndex(lastRouteHotspot.getIndex() + 1);

            double distance = SpatialUtils.calculateDistanceInMeters(
                    lastRouteHotspot.getHotspot().getLocation(),
                    hotspot.getLocation()
            );

            lastRouteHotspot.setDistanceToNext(distance);
            routeHotspotRepository.save(lastRouteHotspot);
        } else {
            newRouteHotspot.setIndex(0);
        }

        routeHotspotRepository.save(newRouteHotspot);
    }

    private void removeHotspotFromRoute(Route route, Hotspot hotspot) {
        List<RouteHotspot> routeHotspots = routeHotspotRepository.findByRoute_RouteIdOrderByIndexAsc(route.getRouteId());

        int targetIndex = -1;
        RouteHotspot targetRouteHotspot = null;

        for (int i = 0; i < routeHotspots.size(); i++) {
            if (routeHotspots.get(i).getHotspot().getHotspotId().equals(hotspot.getHotspotId())) {
                targetIndex = i;
                targetRouteHotspot = routeHotspots.get(i);
                break;
            }
        }

        if (targetRouteHotspot == null) {
            throw new BusinessException("Điểm dừng này không nằm trong tuyến đường");
        }

        if (targetIndex > 0) {
            RouteHotspot prevRouteHotspot = routeHotspots.get(targetIndex - 1);

            if (targetIndex < routeHotspots.size() - 1) {
                RouteHotspot nextRouteHotspot = routeHotspots.get(targetIndex + 1);

                double newDistance = SpatialUtils.calculateDistanceInMeters(
                        prevRouteHotspot.getHotspot().getLocation(),
                        nextRouteHotspot.getHotspot().getLocation()
                );
                prevRouteHotspot.setDistanceToNext(newDistance);
            }
            else {
                prevRouteHotspot.setDistanceToNext(0.0);
            }

            routeHotspotRepository.save(prevRouteHotspot);
        }

        for (int i = targetIndex + 1; i < routeHotspots.size(); i++) {
            RouteHotspot nextRouteHotspot = routeHotspots.get(i);
            nextRouteHotspot.setIndex(nextRouteHotspot.getIndex() - 1);
            routeHotspotRepository.save(nextRouteHotspot);
        }

        routeHotspotRepository.delete(targetRouteHotspot);
    }
}
