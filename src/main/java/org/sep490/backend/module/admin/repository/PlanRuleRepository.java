package org.sep490.backend.module.admin.repository;

import org.sep490.backend.module.admin.entity.PlanRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlanRuleRepository extends JpaRepository<PlanRule, Long> {
    List<PlanRule> findBySubscriptionPlan_SubscriptionPlanId(Long subscriptionPlanId);
}
