package org.sep490.backend.module.exploration.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.service.inter.RouteService;
import org.sep490.backend.module.exploration.dto.response.SavedRouteResponse;
import org.sep490.backend.module.exploration.entity.SavedRoute;
import org.sep490.backend.module.exploration.mapper.SavedRouteMapper;
import org.sep490.backend.module.exploration.repository.SavedRouteRepository;
import org.sep490.backend.module.exploration.service.inter.SavedRouteService;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE,  makeFinal = true)
public class SaveRouteServiceImpl implements SavedRouteService {

    SavedRouteRepository savedRouteRepository;
    UserService userService;
    RouteService routeService;
    SavedRouteMapper savedRouteMapper;


    @Override
    @Transactional
    public SavedRouteResponse saveRoute(Long routeId) {

        User user = userService.getCurrentUser();
        Route route = routeService.getById(routeId);

        if(savedRouteRepository.existsByRoute_RouteIdAndUser_UserId(routeId, user.getUserId())) {
            throw new BusinessException("Tuyến đường đã tồn tại");
        }

        SavedRoute savedRoute = savedRouteMapper.toEntity(route.getRouteId(), user.getUserId());
        savedRouteRepository.save(savedRoute);

        return savedRouteMapper.toResponse(savedRoute);
    }

    @Override
    @Transactional
    public void unsaveRoute(Long savedRouteId) {

        SavedRoute savedRoute = findById(savedRouteId);
        User user = userService.getCurrentUser();

        if(user.getUserId() != savedRoute.getUser().getUserId()) {
            throw new BusinessException("Bạn không có thể bỏ lưu tuyến đường này");
        }

        savedRouteRepository.delete(savedRoute);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavedRouteResponse> findAll() {
        User user = userService.getCurrentUser();
        return savedRouteRepository.findAllByUser_UserId(user.getUserId())
                .stream()
                .map(savedRouteMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SavedRoute findById(Long savedRouteId) {
        return savedRouteRepository.findById(savedRouteId).orElseThrow(() ->  new BusinessException("Route not found"));
    }
}
