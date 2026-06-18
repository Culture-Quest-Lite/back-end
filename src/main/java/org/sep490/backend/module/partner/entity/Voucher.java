package org.sep490.backend.module.partner.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.partner.entity.enumeration.DiscountType;
import org.sep490.backend.module.partner.entity.enumeration.VoucherStatus;
import org.springframework.scheduling.annotation.EnableAsync;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "vouchers", indexes = {
        // Chỉ giữ lại index thông thường cho khóa ngoại
        @Index(name = "idx_voucher_partner", columnList = "partner_id")
},
        uniqueConstraints = {
                // Đưa ràng buộc duy nhất ra đúng phân vùng của nó
                @UniqueConstraint(name = "uk_voucher_code", columnNames = {"code"})
})
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long voucherId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    User partner;

    @Column(name = "voucher_code", nullable = false, unique = true, length = 50)
    String voucherCode;

    @Column(name = "voucher_name", nullable = false)
    String voucherName;

    @Column(columnDefinition = "TEXT")
    String description;

    @Column(name = "discount_type", nullable = false)
    @Enumerated(EnumType.STRING)
    DiscountType discountType;

    @Column(nullable = false)
    BigDecimal discountValue;

    BigDecimal maxDiscountAmount;
    BigDecimal minOrderAmount;

    @Column(name = "points_required", nullable = false)
    Long pointsRequired;

    @Column(name = "quantity_total", nullable = false)
    Long quantityTotal;

    @Column(name = "quantity_remaining", nullable = false)
    Long quantityRemaining;

    @Column(name = "voucher_status")
    @Enumerated(EnumType.STRING)
    VoucherStatus status;

    @Column(name = "start_date", nullable = false)
    LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    LocalDateTime endDate;

    @Column(name = "created_at", nullable = false)
    LocalDateTime createdAt;

    @Column(name = "updated_at")
    LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (quantityRemaining == null) {
            quantityRemaining = quantityTotal;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
