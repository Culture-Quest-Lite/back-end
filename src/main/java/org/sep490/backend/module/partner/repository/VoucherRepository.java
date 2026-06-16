package org.sep490.backend.module.partner.repository;

import org.sep490.backend.module.partner.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long>, JpaSpecificationExecutor<Voucher> {
    Optional<Voucher> findByVoucherCode(String voucherCode);
    boolean existsByVoucherCode(String voucherCode);
    boolean existsByVoucherCodeAndVoucherIdNot(String voucherCode, Long voucherId);
}
