package org.sep490.backend.module.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.sep490.backend.module.admin.entity.enumeration.BillingCycleEnum;
import org.sep490.backend.module.admin.entity.enumeration.InvoicePaymentStatus;
import org.sep490.backend.module.admin.entity.enumeration.InvoiceStatus;
import org.sep490.backend.module.admin.entity.enumeration.PaymentGateway;

import java.time.LocalDateTime;

@Entity
@Table(name = "invoices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invoice_id")
    Long invoiceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_info_id", nullable = false)
    PartnerInfo partnerInfo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_plan_id", nullable = false)
    SubscriptionPlan subscriptionPlan;

    @Column(name = "invoice_code", nullable = false)
    String invoiceCode;

    @Column(name = "billing_cycle")
    @Enumerated(EnumType.STRING)
    BillingCycleEnum billingCycle;

    @Column(name = "start_date")
    LocalDateTime startDate;

    @Column(name = "end_date")
    LocalDateTime endDate;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    InvoiceStatus status;

    @Column(name = "paid_amount")
    Long paidAmount;

    @Column(name = "payment_status")
    @Enumerated(EnumType.STRING)
    InvoicePaymentStatus paymentStatus;

    @Column(name = "payment_gateway")
    @Enumerated(EnumType.STRING)
    PaymentGateway paymentGateway;

    @Column(name = "paid_at")
    LocalDateTime paidAt;

//    @Column(name = "reference_id", length = 255)
//    String referenceId;

    @Column(name = "momo_order_id", unique = true)
    private String momoOrderId;

    @Column(name = "momo_request_id")
    private String momoRequestId;

    @Column(name = "momo_trans_id")
    private String momoTransId;

    @Column(name = "payos_order_code")
    private Long payosOrderCode;

    @Column(name = "payos_payment_link_id")
    private String payosPaymentLinkId;

    @Column(name = "payos_transaction_id")
    private String payosTransactionId;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "refund_order_id")
    private String refundOrderId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

}
