package org.sep490.backend.module.admin.repository;

import org.sep490.backend.module.admin.entity.SubscriptionUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionUsageRepository extends JpaRepository<SubscriptionUsage, Long> {
    List<SubscriptionUsage> findByInvoice_InvoiceId(Long invoiceId);
}
