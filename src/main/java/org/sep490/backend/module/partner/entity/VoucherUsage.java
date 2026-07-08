package org.sep490.backend.module.partner.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.authentication.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "voucher_usages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VoucherUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "voucher_usage_id")
    Long voucherUsageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id", nullable = false)
    Voucher voucher;

    @Column(name = "voucher_code", nullable = false)
    String voucherCode;

    @Column(name = "points_required")
    Long pointsRequired;

    @Column(name = "is_used")
    Boolean isUsed;

    @Column(name = "redeemed_at", nullable = false)
    LocalDateTime redeemedAt;

    @Column(name = "used_at")
    LocalDateTime usedAt;

    @Column(name = "expired_at")
    LocalDateTime expiredAt;

    @PrePersist
    protected void onCreate() {
        redeemedAt = LocalDateTime.now();
        if (isUsed == null) {
            isUsed = false;
        }
    }
}
