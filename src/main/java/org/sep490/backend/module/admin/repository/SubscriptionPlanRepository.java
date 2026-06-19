package org.sep490.backend.module.admin.repository;

import org.sep490.backend.module.admin.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long>, JpaSpecificationExecutor<SubscriptionPlan> {
    boolean existsBySubscriptionPlanNameIgnoreCase(String subscriptionPlanName);
    boolean existsBySubscriptionPlanNameIgnoreCaseAndSubscriptionPlanIdNot(String subscriptionPlanName, Long subscriptionPlanId);
}
