package org.sep490.backend.module.admin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.sep490.backend.module.admin.entity.enumeration.BillingCycleEnum;
import org.sep490.backend.module.admin.entity.enumeration.MomoPaymentStatus;
import org.sep490.backend.module.admin.entity.enumeration.PartnerSubscriptionStatus;
import org.sep490.backend.module.admin.entity.enumeration.PaymentGateway;
import org.sep490.backend.module.authentication.entity.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import org.sep490.backend.module.content.entity.Media;

@Entity
@Table(name = "partner_subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartnerSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User partner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_plan_id", nullable = false)
    private SubscriptionPlan subscriptionPlan;

    @Column(name = "shop_name")
    private String shopName;

    @Column(name = "shop_email", nullable = false, unique = true)
    private String shopEmail;

    @Column(name = "address")
    private String address;

    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point location;

    @Column(name = "billing_cycle")
    @Enumerated(EnumType.STRING)
    private BillingCycleEnum billingCycle;

    @Column(name = "is_verified", nullable = true)
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "document_url")
    private String documentUrl;

    @OneToMany(mappedBy = "partnerSubscription", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Media> medias = new ArrayList<>();

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private PartnerSubscriptionStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "momo_order_id", unique = true)
    private String momoOrderId;

    @Column(name = "momo_request_id")
    private String momoRequestId;

    @Column(name = "momo_trans_id")
    private String momoTransId;

    @Column(name = "paid_amount")
    private Long paidAmount;

    @Column(name = "payment_status")
    @Enumerated(EnumType.STRING)
    private MomoPaymentStatus paymentStatus;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "refund_order_id")
    private String refundOrderId;

    @Column(name = "payos_order_code")
    private Long payosOrderCode;

    @Column(name = "payos_payment_link_id")
    private String payosPaymentLinkId;

    @Column(name = "payos_transaction_id")
    private String payosTransactionId;

    @Column(name = "payment_gateway")
    @Enumerated(EnumType.STRING)
    private PaymentGateway paymentGateway;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
