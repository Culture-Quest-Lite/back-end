package org.sep490.backend.module.admin.repository;

import org.sep490.backend.module.admin.entity.PlanRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlanRuleRepository extends JpaRepository<PlanRule, Long> {
    List<PlanRule> findBySubscriptionPlan_SubscriptionPlanId(Long subscriptionPlanId);

    @Query("""
           SELECT pr
           FROM PlanRule pr
           WHERE pr.subscriptionPlan.subscriptionPlanId IN (
               SELECT i.subscriptionPlan.subscriptionPlanId
               FROM Invoice i
               WHERE i.user.userId = :userId
                 AND i.status = org.sep490.backend.module.admin.entity.enumeration.InvoiceStatus.ACTIVE
                 AND (i.endDate IS NULL OR i.endDate > CURRENT_TIMESTAMP)
           )
           """)
    List<PlanRule> findActiveRulesByUserId(@Param("userId") Long userId);
}
