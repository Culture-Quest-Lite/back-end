package org.sep490.backend.module.admin.repository;

import org.sep490.backend.module.admin.entity.SystemTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemTransactionRepository extends JpaRepository<SystemTransaction, Long> {
    Optional<SystemTransaction> findFirstByGatewayRefOrderByCreatedAtDesc(String gatewayRef);
    List<SystemTransaction> findByInvoice_InvoiceId(Long invoiceId);
}
