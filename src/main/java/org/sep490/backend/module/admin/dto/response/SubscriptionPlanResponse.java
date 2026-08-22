package org.sep490.backend.module.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sep490.backend.module.admin.entity.enumeration.PlanType;
import org.sep490.backend.module.admin.entity.enumeration.SubscriptionPlanStatus;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubscriptionPlanResponse {
    private Long subscriptionPlanId;
    private String subscriptionPlanName;
    private String subscriptionPlanDescription;
    private Long priceMonthly;
    private Long priceYearly;
    private Map<String, Object> configLimit;
    private PlanType planType;
    private SubscriptionPlanStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
