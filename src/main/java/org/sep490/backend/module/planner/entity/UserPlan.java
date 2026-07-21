package org.sep490.backend.module.planner.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.planner.entity.enumeration.PlanStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_plan")
@SQLRestriction("is_deleted is not true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_plan_id")
    Long userPlanId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Column(name = "plan_name", nullable = false, length = 100)
    String name;

    @Column(name = "description", columnDefinition = "TEXT")
    String description;

    @Column(name = "total_stops")
    Integer totalStops;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    PlanStatus status = PlanStatus.DRAFT;

    @Column(name = "started_at")
    LocalDateTime startedAt;

    @Column(name = "start_latitude")
    Double startLatitude;

    @Column(name = "start_longitude")
    Double startLongitude;

    @Column(name = "is_optimized")
    Boolean isOptimized;

    @Column(name = "is_deleted")
    @Builder.Default
    Boolean isDeleted = false;

    @Column(name = "deleted_at")
    LocalDateTime deletedAt;

    @OneToMany(mappedBy = "userPlan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    List<PlanHotspot> planHotspots = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;
}
