package org.sep490.backend.module.admin.repository;

import org.sep490.backend.module.authentication.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    //AuditLog.user là LAZY, fetch sẵn để tránh N+1 khi map ra actor của từng dòng log
    @Override
    @EntityGraph(attributePaths = "user")
    Page<AuditLog> findAll(Specification<AuditLog> spec, Pageable pageable);
}
