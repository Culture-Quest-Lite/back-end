package org.sep490.backend.module.admin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.sep490.backend.module.admin.entity.enumeration.BillingCycleEnum;
import org.sep490.backend.module.admin.entity.enumeration.PartnerSubscriptionStatus;
import org.sep490.backend.module.authentication.entity.User;

import java.time.LocalDateTime;

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

    @Column(name = "address")
    private String address;

    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point location;

    @Column(name = "billing_cycle")
    @Enumerated(EnumType.STRING)
    private BillingCycleEnum billingCycle;

    @Column(name = "is_verified", nullable = true)
    private Boolean isVerified = false;

    @Column(name = "document_url")
    private String documentUrl;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private PartnerSubscriptionStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
