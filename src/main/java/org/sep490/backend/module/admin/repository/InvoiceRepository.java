package org.sep490.backend.module.admin.repository;

import org.sep490.backend.module.admin.dto.projection.RevenueSummaryProjection;
import org.sep490.backend.module.admin.entity.Invoice;
import org.sep490.backend.module.admin.entity.enumeration.InvoicePaymentStatus;
import org.sep490.backend.module.admin.entity.enumeration.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Toàn bộ số liệu doanh thu trong MỘT lượt quét bảng invoices.
     * partnerInfo/user là hai FK loại trừ nhau: partnerInfo != null = doanh thu đối tác,
     * user != null = doanh thu premium. So sánh IS NOT NULL trên FK không gây join.
     * Mốc tháng dùng COALESCE(paidAt, createdAt) vì paidAt chỉ được ghi qua webhook PayOS.
     */
    @Query("SELECT " +
            "COALESCE(SUM(CASE WHEN i.paymentStatus = :paid THEN COALESCE(i.paidAmount, 0L) ELSE 0L END), 0L) AS totalRevenue, " +
            "COALESCE(SUM(CASE WHEN i.paymentStatus = :paid AND i.partnerInfo IS NOT NULL THEN COALESCE(i.paidAmount, 0L) ELSE 0L END), 0L) AS partnerRevenue, " +
            "COALESCE(SUM(CASE WHEN i.paymentStatus = :paid AND i.user IS NOT NULL THEN COALESCE(i.paidAmount, 0L) ELSE 0L END), 0L) AS premiumRevenue, " +
            "COALESCE(SUM(CASE WHEN i.paymentStatus = :paid AND COALESCE(i.paidAt, i.createdAt) >= :monthStart THEN COALESCE(i.paidAmount, 0L) ELSE 0L END), 0L) AS revenueThisMonth, " +
            "COALESCE(SUM(CASE WHEN i.paymentStatus = :paid AND COALESCE(i.paidAt, i.createdAt) >= :prevMonthStart AND COALESCE(i.paidAt, i.createdAt) < :monthStart THEN COALESCE(i.paidAmount, 0L) ELSE 0L END), 0L) AS revenueLastMonth, " +
            "COALESCE(SUM(CASE WHEN i.paymentStatus = :paid THEN 1L ELSE 0L END), 0L) AS paidInvoices, " +
            "COALESCE(SUM(CASE WHEN i.status = :activeStatus AND i.partnerInfo IS NOT NULL AND (i.endDate IS NULL OR i.endDate > :now) THEN 1L ELSE 0L END), 0L) AS activePartnerSubscriptions, " +
            "COALESCE(SUM(CASE WHEN i.status = :activeStatus AND i.user IS NOT NULL AND (i.endDate IS NULL OR i.endDate > :now) THEN 1L ELSE 0L END), 0L) AS activePremiumSubscriptions " +
            "FROM Invoice i")
    RevenueSummaryProjection summarizeRevenue(@Param("paid") InvoicePaymentStatus paid,
                                              @Param("activeStatus") InvoiceStatus activeStatus,
                                              @Param("monthStart") LocalDateTime monthStart,
                                              @Param("prevMonthStart") LocalDateTime prevMonthStart,
                                              @Param("now") LocalDateTime now);
}
