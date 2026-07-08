package org.sep490.backend.module.partner.repository;

import org.sep490.backend.module.partner.entity.VoucherUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, Long> {
    boolean existsByUserUserIdAndVoucherVoucherId(Long userId, Long voucherId);
    Page<VoucherUsage> findByUser_UserId(Long userId, Pageable pageable);
    Optional<VoucherUsage> findByVoucherCode(String voucherCode);
}
