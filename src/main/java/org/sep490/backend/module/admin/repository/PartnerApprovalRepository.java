package org.sep490.backend.module.admin.repository;

import org.sep490.backend.module.admin.entity.PartnerApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PartnerApprovalRepository extends JpaRepository<PartnerApproval, Long> {
}
