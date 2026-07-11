package org.sep490.backend.module.user.repository;

import org.sep490.backend.module.user.entity.LevelProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LevelProgressRepository extends JpaRepository<LevelProgress, Long> {
    List<LevelProgress> findByUserUserIdOrderByUnlockedAtAsc(Long userId);
    boolean existsByUser_UserIdAndLevel_LevelId(Long userId, Long levelId);
}
