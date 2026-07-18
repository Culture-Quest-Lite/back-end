package org.sep490.backend.module.admin.repository;

import org.sep490.backend.module.admin.entity.Invoice;
import org.sep490.backend.module.admin.entity.enumeration.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByPartnerInfo_User_UserIdOrderByCreatedAtDesc(Long userId);
    List<Invoice> findAllByOrderByCreatedAtDesc();
    List<Invoice> findByStatusOrderByCreatedAtDesc(InvoiceStatus status);
    Optional<Invoice> findByPayosOrderCode(Long payosOrderCode);
}
