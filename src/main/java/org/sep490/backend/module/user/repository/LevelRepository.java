package org.sep490.backend.module.user.repository;

import org.sep490.backend.module.user.entity.Level;
import org.sep490.backend.module.user.entity.enumeration.LevelStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LevelRepository extends JpaRepository<Level, Long> {
    Optional<Level> findFirstByStatusOrderByRequiredXpAsc(LevelStatus status);
    List<Level> findAllByStatusNot(LevelStatus status);
    Optional<Level> findByLevelIdAndStatusNot(Long levelId, LevelStatus status);
    boolean existsByNameAndStatusNot(String name, LevelStatus status);
    boolean existsByNameAndLevelIdNotAndStatusNot(String name, Long levelId, LevelStatus status);
    boolean existsByRequiredXpAndStatusNot(Integer requiredXp, LevelStatus status);
    boolean existsByRequiredXpAndLevelIdNotAndStatusNot(Integer requiredXp, Long levelId, LevelStatus status);
    Optional<Level> findFirstByStatusAndRequiredXpLessThanEqualOrderByRequiredXpDesc(LevelStatus status, Integer xp);
}
