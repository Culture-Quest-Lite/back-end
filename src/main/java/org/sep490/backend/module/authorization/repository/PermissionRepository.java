package org.sep490.backend.module.authorization.repository;

import org.sep490.backend.module.authorization.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByCode(String code);
    List<Permission> findAllByActiveTrueOrderByGroupNameAscCodeAsc();
    boolean existsByCode(String code);
}
