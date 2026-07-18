package org.sep490.backend.module.admin.repository;

import org.sep490.backend.module.admin.entity.Invoice;
import org.sep490.backend.module.admin.entity.enumeration.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByPartnerInfo_User_UserIdOrderByCreatedAtDesc(Long userId);
    List<Invoice> findAllByOrderByCreatedAtDesc();
    List<Invoice> findByStatusOrderByCreatedAtDesc(InvoiceStatus status);
    Optional<Invoice> findByPayosOrderCode(Long payosOrderCode);

    List<Invoice> findByUser_UserIdOrderByCreatedAtDesc(Long userId);
    Optional<Invoice> findFirstByUser_UserIdAndStatusOrderByEndDateDesc(Long userId, InvoiceStatus status);
    boolean existsByUser_UserIdAndStatusAndEndDateAfter(Long userId, InvoiceStatus status, LocalDateTime now);
    List<Invoice> findByUserIsNotNullAndStatusAndEndDateBefore(InvoiceStatus status, LocalDateTime now);
    List<Invoice> findByPartnerInfoIsNotNullOrderByCreatedAtDesc();
    List<Invoice> findByPartnerInfoIsNotNullAndStatusOrderByCreatedAtDesc(InvoiceStatus status);
}
