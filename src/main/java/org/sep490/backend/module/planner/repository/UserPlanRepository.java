package org.sep490.backend.module.planner.repository;

import org.sep490.backend.module.planner.entity.UserPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface UserPlanRepository extends JpaRepository<UserPlan, Long>, JpaSpecificationExecutor<UserPlan> {
    List<UserPlan> findByUser_UserIdOrderByCreatedAtDesc(Long userId);
}
