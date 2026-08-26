package org.sep490.backend.module.partner.repository;

import org.sep490.backend.module.admin.dto.projection.DailyCountProjection;
import org.sep490.backend.module.partner.dto.projection.TopVoucherProjection;
import org.sep490.backend.module.partner.entity.VoucherUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, Long> {
    boolean existsByUserUserIdAndVoucherVoucherId(Long userId, Long voucherId);
    Page<VoucherUsage> findByUser_UserId(Long userId, Pageable pageable);
    Optional<VoucherUsage> findByVoucherCode(String voucherCode);

    @Query("SELECT YEAR(vu.redeemedAt) AS bucketYear, MONTH(vu.redeemedAt) AS bucketMonth, " +
            "DAY(vu.redeemedAt) AS bucketDay, COUNT(vu) AS total " +
            "FROM VoucherUsage vu " +
            "WHERE vu.voucher.partner.userId = :partnerId " +
            "AND vu.redeemedAt >= :from AND vu.redeemedAt < :to " +
            "GROUP BY YEAR(vu.redeemedAt), MONTH(vu.redeemedAt), DAY(vu.redeemedAt)")
    List<DailyCountProjection> countRedemptionsPerDay(@Param("partnerId") Long partnerId,
                                                      @Param("from") LocalDateTime from,
                                                      @Param("to") LocalDateTime to);

    @Query("SELECT vu.voucher.voucherId AS voucherId, vu.voucher.voucherName AS voucherName, " +
            "vu.voucher.voucherCode AS voucherCode, COUNT(vu) AS redemptionCount " +
            "FROM VoucherUsage vu " +
            "WHERE vu.voucher.partner.userId = :partnerId " +
            "GROUP BY vu.voucher.voucherId, vu.voucher.voucherName, vu.voucher.voucherCode " +
            "ORDER BY COUNT(vu) DESC")
    List<TopVoucherProjection> findTopRedeemedVouchers(@Param("partnerId") Long partnerId, Pageable pageable);

    List<VoucherUsage> findByExpiredAtBetweenAndIsUsedFalse(LocalDateTime start, LocalDateTime end);
    Optional<VoucherUsage> findByVoucherUsageCode(String voucherUsageCode);
}
