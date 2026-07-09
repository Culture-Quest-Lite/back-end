package org.sep490.backend.module.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.sep490.backend.module.admin.entity.enumeration.SystemTransactionStatus;
import org.sep490.backend.module.admin.entity.enumeration.SystemTransactionType;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SystemTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "system_transaction_id")
    Long systemTransactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    Invoice invoice;

    @Column(name = "transaction_type")
    @Enumerated(EnumType.STRING)
    SystemTransactionType transactionType;

    @Column(name = "amount")
    Long amount;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    SystemTransactionStatus status;

    @Column(name = "gateway_ref", length = 255)
    String gatewayRef;

    @Column(name = "notes", columnDefinition = "TEXT")
    String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;
}
