package org.sep490.backend.module.authentication.repository;

import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByKeycloakUserId(String keycloakUserId);
    boolean existsByLevel_LevelId(Long levelId);

    @Modifying
    @Query("UPDATE User u SET u.totalPoints = u.totalPoints - :points WHERE u.userId = :userId AND u.totalPoints >= :points")
    int deductPoints(@Param("userId") Long userId, @Param("points") Integer points);

    @Query(value = "SELECT u FROM User u LEFT JOIN FETCH u.level " +
            "WHERE u.status = :status AND u.role = :role " +
            "ORDER BY COALESCE(u.totalXp, 0) DESC, u.createdAt ASC, u.userId ASC",
            countQuery = "SELECT COUNT(u) FROM User u WHERE u.status = :status AND u.role = :role")
    Page<User> findLeaderboardByXp(@Param("status") UserStatus status,
                                   @Param("role") UserRole role,
                                   Pageable pageable);

    long countByStatusAndRole(UserStatus status, UserRole role);

    @Query("SELECT COUNT(u) FROM User u " +
            "WHERE u.status = :status AND u.role = :role " +
            "AND (COALESCE(u.totalXp, 0) > :xp " +
            "  OR (COALESCE(u.totalXp, 0) = :xp AND u.createdAt < :createdAt) " +
            "  OR (COALESCE(u.totalXp, 0) = :xp AND u.createdAt = :createdAt AND u.userId < :userId))")
    long countUsersRankedAbove(@Param("status") UserStatus status,
                               @Param("role") UserRole role,
                               @Param("xp") Integer xp,
                               @Param("createdAt") LocalDateTime createdAt,
                               @Param("userId") Long userId);
}
