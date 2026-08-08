package org.sep490.backend.module.planner.service;

import org.sep490.backend.module.planner.dto.request.CreateCustomPlanRequest;
import org.sep490.backend.module.planner.dto.response.UserPlanResponse;

import java.util.List;

public interface UserPlanService {
    UserPlanResponse create(CreateCustomPlanRequest request);
    UserPlanResponse update(Long planId, CreateCustomPlanRequest request);
    UserPlanResponse getById(Long planId);
    List<UserPlanResponse> getMyPlans();
    UserPlanResponse start(Long planId);
    void delete(Long planId);
}
