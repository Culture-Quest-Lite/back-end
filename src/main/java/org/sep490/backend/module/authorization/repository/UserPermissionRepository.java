package org.sep490.backend.module.authorization.repository;

import org.sep490.backend.module.authorization.entity.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {


    @Query("""
           select up
           from UserPermission up
           join fetch up.permission p
           where up.user.userId = :userId
             and p.active = true
             and (up.expiresAt is null or up.expiresAt > CURRENT_TIMESTAMP)
           """)
    List<UserPermission> findActiveByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT up
        FROM UserPermission up
        JOIN FETCH up.permission
        WHERE up.user.userId = :userId
        """)
    List<UserPermission> findAllByUserIdWithPermission(@Param("userId") Long userId);

    Optional<UserPermission> findByUser_UserIdAndPermission_Code(Long userId, String code);

    @Modifying
    void deleteByUser_UserIdAndPermission_Code(Long userId, String code);
}
