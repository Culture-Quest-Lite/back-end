package org.sep490.backend.module.partner.repository;

import org.sep490.backend.module.partner.entity.UserVoucher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserVoucherRepository extends JpaRepository<UserVoucher, Long> {
    boolean existsByUserUserIdAndVoucherVoucherId(Long userId, Long voucherId);
    Page<UserVoucher> findByUser_UserId(Long userId, Pageable pageable);
}
