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
@Table(name = "reward_transaction", indexes = {
        @Index(name = "idx_reward_trans_user_created", columnList = "user_id, created_at")
})
public class RewardTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reward_transaction_id")
    private Long transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "transaction_type")
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Column(name = "points_amount")
    private Long pointsAmount;

    @Column(name = "xp_amount")
    private Long xpAmount;

    @Column(name = "points_balance")
    private Long pointsBalance;

    @Column(name = "xp_balance")
    private Long xpBalance;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "description")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
