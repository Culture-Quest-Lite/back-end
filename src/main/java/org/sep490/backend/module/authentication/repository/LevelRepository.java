package org.sep490.backend.module.authentication.repository;

import org.sep490.backend.module.authentication.entity.Level;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LevelRepository extends JpaRepository<Level, Long> {
    Optional<Level> findFirstByOrderByRequiredXpAsc();
}
