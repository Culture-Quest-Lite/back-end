package org.sep490.backend.module.gamification.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.gamification.entity.enumeration.TransactionType;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "point_transaction", indexes = {
        //Lấy lịch sử giao dịch điểm của 1 User, bản ghi mới nhất xếp trước
        @Index(name = "idx_point_trans_history", columnList = "user_id, created_at")
})
public class PointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "transaction_type")
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Column(name = "point_amount")
    private Long pointAmount;

    @Column(name = "description")
    private String description;

    @Column(name = "balance_remaining")
    private Long balanceRemaining;

    private Long referenceId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

}
