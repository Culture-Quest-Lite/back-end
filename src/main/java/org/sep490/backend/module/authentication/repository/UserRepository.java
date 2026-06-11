package org.sep490.backend.module.authentication.repository;

import org.sep490.backend.module.authentication.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByKeycloakUserId(String keycloakUserId);
    boolean existsByLevel_LevelId(Long levelId);
}
