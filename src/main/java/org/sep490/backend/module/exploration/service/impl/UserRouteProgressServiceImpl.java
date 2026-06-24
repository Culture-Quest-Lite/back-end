package org.sep490.backend.module.exploration.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.repository.RouteHotspotRepository;
import org.sep490.backend.module.content.service.inter.RouteService;
import org.sep490.backend.module.exploration.dto.filter.UserRouteProgressFilter;
import org.sep490.backend.module.exploration.dto.response.CheckInHotspotResponse;
import org.sep490.backend.module.exploration.dto.response.UserRouteProgressDetailResponse;
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

@Service
@Transactional
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE,  makeFinal = true)
public class UserRouteProgressServiceImpl implements UserRouteProgressService {

    UserRouteProgressRepository userRouteProgressRepository;
    RouteHotspotRepository routeHotspotRepository;
    UserService userService;
    RouteService routeService;
    UserRouteProgressMapper userRouteProgressMapper;

    @Override
    public HashMap<Integer, UserRouteProgressResponse> startRouteProgress(Long routeId) {

        User user = userService.getCurrentUser();
        Route route = routeService.getById(routeId);
        UserRouteProgress userRouteProgress = userRouteProgressRepository
                .findByRoute_RouteIdAndUser_UserId(route.getRouteId(), user.getUserId()).orElse(null);
        HashMap<Integer, UserRouteProgressResponse> result = new HashMap<>();

        if(userRouteProgress == null) {
            userRouteProgress = userRouteProgressMapper.toEntity(route, user, 0, 0.0);
            // update to check in hotspots.
            userRouteProgress = userRouteProgressRepository.save(userRouteProgress);
            result.put(201, userRouteProgressMapper.toResponse(userRouteProgress));
        } else {
            if(userRouteProgress.getStatus() == ProgressStatus.IN_PROGRESS) {
                throw new BusinessException("Bạn hiện đang trong tuyến đường này");
            } else {
                userRouteProgress.setStatus(ProgressStatus.IN_PROGRESS);
                userRouteProgress = userRouteProgressRepository.save(userRouteProgress);
                result.put(200, userRouteProgressMapper.toResponse(userRouteProgress));

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
            throw new BusinessException("Bạn chưa bắt đầu hành trình " + route.getRouteName());
        } else {
            if(userRouteProgress.getStatus() == ProgressStatus.ABANDONED) {
                throw new BusinessException("Bạn đã kết thúc tuyến đường này");
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

    @Override
    public UserRouteProgressDetailResponse getRouteProgress(Long progressId) {
        UserRouteProgress userRouteProgress = getById(progressId);
        Route route = userRouteProgress.getRoute();
        User progressUser = userRouteProgress.getUser();
        User currentUser = userService.getCurrentUser();

        if(!progressUser.equals(currentUser)) {
            throw new BusinessException("Bạn chưa bắt đầu tuyến đường này");
        }

        List<CheckInHotspotResponse> hotspot = routeHotspotRepository
                .getHotspotCheckInStatusByRouteAndUser(route.getRouteId(), currentUser.getUserId());

        UserRouteProgressDetailResponse detailResponse = userRouteProgressMapper.toDetailResponse(userRouteProgress);
        detailResponse.setHotspotProgressList(hotspot);

        return detailResponse;
    }

    @Override
    public UserRouteProgress getById(Long progressId) {
        return userRouteProgressRepository.findById(progressId).orElseThrow(() -> new BusinessException("User route progress not found"));
    }
}
