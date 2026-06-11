package org.sep490.backend.module.content.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.dto.request.RouteHotspotRequest;
import org.sep490.backend.module.content.dto.request.RouteRequest;
import org.sep490.backend.module.content.dto.response.RouteResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.RouteHotspot;
import org.sep490.backend.module.content.enums.ContentStatus;
import org.sep490.backend.module.content.mapper.RouteMapper;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.RouteHotspotRepository;
import org.sep490.backend.module.content.repository.RouteRepository;
import org.sep490.backend.module.content.service.inter.RouteService;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE,  makeFinal = true)
public class RouteServiceImpl implements RouteService {

    RouteRepository routeRepository;
    RouteMapper routeMapper;
    RouteHotspotRepository routeHotspotRepository;
    HotspotRepository hotspotRepository;
    UserService userService;

    @Override
    @Transactional
    public RouteResponse create(RouteRequest request) {

        Route route = routeMapper.toEntity(request);
        //User creator = userService.getCurrentUser();

        //route.setCreatedBy(creator);
        route.setStatus(ContentStatus.DRAFT);
        route.setIsLocked(false);

        route = routeRepository.save(route);

        List<RouteHotspot> routeHotspots = processRouteHotspots(route, request.getHotspots());

        return buildRouteResponse(route, routeHotspots);
    }

    @Override
    @Transactional
    public RouteResponse update(Long id, RouteRequest request) {

        Route currRoute = getById(id);

        routeMapper.updateFromRequest(currRoute, request);
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

        routeHotspotRepository.deleteByRoute_RouteId(id);
        routeRepository.delete(route);
    }

    @Override
    @Transactional(readOnly = true)
    public Route getById(Long id) {

        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tuyến đường không tồn tại"));

        return route;
    }

    private List<RouteHotspot> processRouteHotspots(Route route, List<RouteHotspotRequest> hotspotRequests) {

        if (hotspotRequests == null || hotspotRequests.isEmpty()) {
            return new ArrayList<>();
        }

        List<RouteHotspot> routeHotspots = new ArrayList<>();

        for (int i = 0; i < hotspotRequests.size(); i++) {

            RouteHotspotRequest req = hotspotRequests.get(i);

            Hotspot hotspot = hotspotRepository.findById(req.getHotspotId())
                    .orElseThrow(() -> new RuntimeException("Hotspot ID " + req.getHotspotId() + " không tồn tại"));

            RouteHotspot routeHotspot = RouteHotspot.builder()
                    .route(route)
                    .hotspot(hotspot)
                    .index(req.getIndex() != null ? req.getIndex() : i)
                    .build();

            if (i < hotspotRequests.size() - 1) {
                routeHotspot.setDistanceToNext(0.0);
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
}
