package org.sep490.backend.module.admin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.sep490.backend.module.admin.entity.enumeration.PlanType;
import org.sep490.backend.module.admin.entity.enumeration.SubscriptionPlanStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name = "subscription_plan")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long subscriptionPlanId;

    @Column(name = "subscription_plan_name", nullable = false, unique = true, length = 100)
    private String subscriptionPlanName;

    @Column(name = "subscription_plan_description", length = 500)
    private String subscriptionPlanDescription;

    @Column(name = "price_monthly")
    private Long priceMonthly;

    @Column(name = "price_yearly")
    private Long priceYearly;

    @Column(name = "config_limit", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String configLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false)
    private PlanType planType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private SubscriptionPlanStatus status;

    @OneToMany(mappedBy = "subscriptionPlan", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Invoice> shopSubscriptions = new ArrayList<>();
}
