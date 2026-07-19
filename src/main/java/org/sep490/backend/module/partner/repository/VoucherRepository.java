package org.sep490.backend.module.partner.repository;

import org.sep490.backend.module.partner.entity.Voucher;
import org.sep490.backend.module.partner.entity.enumeration.VoucherStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long>, JpaSpecificationExecutor<Voucher> {
    Optional<Voucher> findByVoucherCode(String voucherCode);
    boolean existsByVoucherCode(String voucherCode);
    boolean existsByVoucherCodeAndVoucherIdNot(String voucherCode, Long voucherId);

    @Modifying
    @Query("UPDATE Voucher v SET v.quantityRemaining = v.quantityRemaining - 1 WHERE v.voucherId = :voucherId AND v.quantityRemaining > 0")
    int decrementQuantityRemaining(@Param("voucherId") Long voucherId);

    @Modifying
    @Query("UPDATE Voucher v SET v.status = :expiredStatus, v.updatedAt = :now WHERE v.endDate < :now AND v.status IN (:statuses)")
    int expireVouchers(@Param("expiredStatus") VoucherStatus expiredStatus,
                       @Param("statuses") Collection<VoucherStatus> statuses,
                       @Param("now") LocalDateTime now);
}
