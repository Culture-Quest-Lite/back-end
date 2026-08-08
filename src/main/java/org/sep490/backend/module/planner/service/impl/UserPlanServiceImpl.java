package org.sep490.backend.module.planner.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.exploration.repository.UserHotspotProgressRepository;
import org.sep490.backend.module.planner.dto.request.CreateCustomPlanRequest;
import org.sep490.backend.module.planner.dto.request.PlanStopRequest;
import org.sep490.backend.module.planner.dto.response.PlanHotspotResponse;
import org.sep490.backend.module.planner.dto.response.UserPlanResponse;
import org.sep490.backend.module.planner.entity.PlanHotspot;
import org.sep490.backend.module.planner.entity.UserPlan;
import org.sep490.backend.module.planner.entity.enumeration.PlanStatus;
import org.sep490.backend.module.planner.mapper.PlanMapper;
import org.sep490.backend.module.planner.repository.UserPlanRepository;
import org.sep490.backend.module.planner.service.UserPlanService;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserPlanServiceImpl implements UserPlanService {

    UserPlanRepository userPlanRepository;
    UserHotspotProgressRepository userHotspotProgressRepository;
    HotspotService hotspotService;
    UserService userService;
    PlanMapper planMapper;

    @Override
    @Transactional
    public UserPlanResponse create(CreateCustomPlanRequest request) {
        UserPlan plan = planMapper.toEntity(request);
        plan.setUser(userService.getCurrentUser());
        plan.setStatus(PlanStatus.READY);
        plan.setTotalStops(request.getStops().size());

        applyStops(plan, request.getStops());

        plan = userPlanRepository.save(plan);
        return toResponseWithProgress(plan);
    }


    @Override
    @Transactional
    public UserPlanResponse update(Long planId, CreateCustomPlanRequest request) {
        UserPlan plan = getEntityOwned(planId);
        if (plan.getStatus() == PlanStatus.STARTED) {
            throw new BusinessException("Kế hoạch đã bắt đầu hành trình, không thể chỉnh sửa");
        }

        planMapper.updateFromRequest(plan, request);
        plan.setTotalStops(request.getStops().size());
        plan.getPlanHotspots().clear();
        applyStops(plan, request.getStops());

        plan = userPlanRepository.save(plan);
        return toResponseWithProgress(plan);
    }

    @Override
    @Transactional(readOnly = true)
    public UserPlanResponse getById(Long planId) {
        return toResponseWithProgress(getEntityOwned(planId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserPlanResponse> getMyPlans() {
        User user = userService.getCurrentUser();
        return userPlanRepository.findByUser_UserIdOrderByCreatedAtDesc(user.getUserId())
                .stream().map(this::toResponseWithProgress).toList();
    }

    @Override
    @Transactional
    public UserPlanResponse start(Long planId) {
        UserPlan plan = getEntityOwned(planId);

        if (plan.getPlanHotspots() == null || plan.getPlanHotspots().isEmpty()) {
            throw new BusinessException("Kế hoạch chưa có điểm dừng nào");
        }

        if (plan.getStatus() == PlanStatus.STARTED) {
            throw new BusinessException("Bạn đã bắt đầu kế hoạch này rồi");
        }

        plan.setStatus(PlanStatus.STARTED);
        plan.setStartedAt(LocalDateTime.now());
        plan = userPlanRepository.save(plan);

        return toResponseWithProgress(plan);
    }

    @Override
    @Transactional
    public void delete(Long planId) {
        UserPlan plan = getEntityOwned(planId);
        plan.setIsDeleted(true);
        plan.setDeletedAt(LocalDateTime.now());
        userPlanRepository.save(plan);
    }

    private void applyStops(UserPlan plan, List<PlanStopRequest> stops) {
        for (int i = 0; i < stops.size(); i++) {
            PlanStopRequest s = stops.get(i);
            PlanHotspot planHotspot = planMapper.toEntity(s);
            planHotspot.setUserPlan(plan);
            planHotspot.setHotspot(hotspotService.getById(s.getHotspotId()));
            planHotspot.setStopIndex(i + 1);
            plan.getPlanHotspots().add(planHotspot);
        }
    }
    
    private UserPlanResponse toResponseWithProgress(UserPlan plan) {
        UserPlanResponse response = planMapper.toResponse(plan);
        Long userId = plan.getUser().getUserId();

        int completed = 0;
        for (PlanHotspotResponse stop : response.getStops()) {
            boolean checkedIn = userHotspotProgressRepository
                    .existsByUser_UserIdAndHotspot_HotspotId(userId, stop.getHotspot().getHotspotId());
            stop.setIsCheckedIn(checkedIn);
            if (checkedIn) {
                completed++;
            } else if (stop.getHotspot() != null) {
                stop.getHotspot().setStories(Collections.emptyList());
            }
        }

        int total = response.getStops().size();
        response.setCompletedStops(completed);
        response.setProgressPercentage(total > 0 ? (completed * 100.0) / total : 0.0);
        return response;
    }

    private UserPlan getEntityOwned(Long planId) {
        UserPlan plan = userPlanRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("Kế hoạch không tồn tại"));
        User current = userService.getCurrentUser();
        if (!plan.getUser().getUserId().equals(current.getUserId())) {
            throw new BusinessException("Bạn không có quyền truy cập kế hoạch này");
        }
        return plan;
    }
}
