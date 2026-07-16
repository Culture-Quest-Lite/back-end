package org.sep490.backend.module.planner.repository;

import org.sep490.backend.module.planner.entity.PlanHotspot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanHotspotRepository extends JpaRepository<PlanHotspot, Long> {
    List<PlanHotspot> findByUserPlan_UserPlanIdOrderByStopIndexAsc(Long userPlanId);
    void deleteByUserPlan_UserPlanId(Long userPlanId);
}
