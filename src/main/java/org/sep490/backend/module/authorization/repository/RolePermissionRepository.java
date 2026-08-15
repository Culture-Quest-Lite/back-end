package org.sep490.backend.module.authorization.repository;

import org.sep490.backend.module.authorization.entity.RolePermission;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    boolean existsByRoleAndPermission_Code(UserRole role, String code);

    @Query("""
        SELECT p.code
        FROM RolePermission rp
        JOIN rp.permission p
        WHERE rp.role = :role and p.active = true
""")
    List<String> findPermissionCodesByRole(@Param("role") UserRole role);

    @Query("SELECT DISTINCT rp.permission.code FROM RolePermission rp")
    List<String> findMappedPermissionCodes();

    @Modifying
    void deleteByRole(UserRole role);

    @Modifying
    void deleteByRoleAndPermission_Code(UserRole role, String code);
}
