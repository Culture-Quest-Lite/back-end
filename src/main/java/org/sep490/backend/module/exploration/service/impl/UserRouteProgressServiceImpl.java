package org.sep490.backend.module.exploration.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.service.inter.RouteService;
import org.sep490.backend.module.exploration.dto.filter.UserRouteProgressFilter;
import org.sep490.backend.module.exploration.dto.response.UserRouteProgressResponse;
import org.sep490.backend.module.exploration.entity.UserRouteProgress;
import org.sep490.backend.module.exploration.entity.enumuration.ProgressStatus;
import org.sep490.backend.module.exploration.mapper.UserRouteProgressMapper;
import org.sep490.backend.module.exploration.repository.UserRouteProgressRepository;
import org.sep490.backend.module.exploration.service.inter.UserRouteProgressService;
import org.sep490.backend.module.exploration.specification.UserRouteProgressSpecification;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE,  makeFinal = true)
public class UserRouteProgressServiceImpl implements UserRouteProgressService {

    UserRouteProgressRepository userRouteProgressRepository;
    UserService userService;
    RouteService routeService;
    UserRouteProgressMapper userRouteProgressMapper;

    @Override
    public HashMap<Integer, UserRouteProgressResponse> startRouteProgress(Long routeId) {

        User user = userService.getCurrentUser();
        Route route = routeService.getById(routeId);
        UserRouteProgress userRouteProgress = userRouteProgressRepository
                .findByRoute_RouteIdAndUser_UserId(route.getRouteId(), user.getUserId()).orElse(null);
        int code = 200;
        HashMap<Integer, UserRouteProgressResponse> result = new HashMap<>();

        if(userRouteProgress == null) {
            userRouteProgress = userRouteProgressMapper.toEntity(route, user, 0, 0.0);
            // update to check in hotspots.
            userRouteProgress = userRouteProgressRepository.save(userRouteProgress);
            code = 201;
            result.put(code, userRouteProgressMapper.toResponse(userRouteProgress));
        } else {
            if(userRouteProgress.getStatus() == ProgressStatus.IN_PROGRESS) {
                throw new BusinessException("Route is already in progress");
            } else {
                userRouteProgress.setStatus(ProgressStatus.IN_PROGRESS);
                userRouteProgress = userRouteProgressRepository.save(userRouteProgress);
                result.put(code, userRouteProgressMapper.toResponse(userRouteProgress));

            }
        }

        return result;
    }

    @Override
    public UserRouteProgressResponse abandonRouteProgress(Long routeId) {
        User user = userService.getCurrentUser();
        Route route = routeService.getById(routeId);
        UserRouteProgress userRouteProgress = userRouteProgressRepository
                .findByRoute_RouteIdAndUser_UserId(route.getRouteId(), user.getUserId()).orElse(null);

        if(userRouteProgress == null) {
            throw new BusinessException("User has not start this route");
        } else {
            if(userRouteProgress.getStatus() == ProgressStatus.ABANDONED) {
                throw new BusinessException("Route is already abandoned");
            } else {
                userRouteProgress.setStatus(ProgressStatus.ABANDONED);
                userRouteProgress = userRouteProgressRepository.save(userRouteProgress);
            }
        }

        return userRouteProgressMapper.toResponse(userRouteProgress);
    }

    @Override
    public Page<UserRouteProgressResponse> getAll(UserRouteProgressFilter filter) {
        User user = userService.getCurrentUser();

        Sort sort = filter.getSortDir().equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(filter.getSortBy()).ascending()
                : Sort.by(filter.getSortBy()).descending();
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Specification<UserRouteProgress> spec = UserRouteProgressSpecification.filterProgress(filter, user);

        return userRouteProgressRepository.findAll(spec, pageable).map(userRouteProgressMapper::toResponse);
    }
}
