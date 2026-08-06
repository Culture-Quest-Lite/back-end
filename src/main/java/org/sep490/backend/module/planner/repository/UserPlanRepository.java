package org.sep490.backend.module.planner.repository;

import org.sep490.backend.module.planner.entity.UserPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserPlanRepository extends JpaRepository<UserPlan, Long>, JpaSpecificationExecutor<UserPlan> {
    List<UserPlan> findByUser_UserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT p.user.userId FROM UserPlan p WHERE p.userPlanId = :id")
    Optional<Long> findOwnerId(@Param("id") Long id);
}
