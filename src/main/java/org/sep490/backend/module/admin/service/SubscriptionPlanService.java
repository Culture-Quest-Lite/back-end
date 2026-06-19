package org.sep490.backend.module.admin.service;

import org.sep490.backend.module.admin.dto.filter.SubscriptionPlanFilterRequest;
import org.sep490.backend.module.admin.dto.request.SubscriptionPlanRequest;
import org.sep490.backend.module.admin.dto.response.SubscriptionPlanResponse;
import org.sep490.backend.module.admin.entity.SubscriptionPlan;
import org.springframework.data.domain.Page;

public interface SubscriptionPlanService {
    SubscriptionPlanResponse createSubscriptionPlan(SubscriptionPlanRequest request);
    SubscriptionPlanResponse updateSubscriptionPlan(Long id, SubscriptionPlanRequest request);
    SubscriptionPlanResponse getSubscriptionPlanDetail(Long id);
    Page<SubscriptionPlanResponse> getAllWithFilter(SubscriptionPlanFilterRequest filter);
    void deleteSubscriptionPlan(Long id);
    SubscriptionPlan getSubscriptionPlanById(Long id);
}
