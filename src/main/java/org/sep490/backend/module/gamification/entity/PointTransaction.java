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
@Table(name = "point_transaction")
@AllArgsConstructor
@NoArgsConstructor
@Builder
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
