package org.sep490.backend.module.planner.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.sep490.backend.module.content.entity.Hotspot;

import java.time.LocalDateTime;

@Entity
@Table(name = "plan_hotspot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlanHotspot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_hotspot_id")
    Long planHotspotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_plan_id", nullable = false)
    UserPlan userPlan;

    @ManyToOne
    @JoinColumn(name = "hotspot_id", nullable = false)
    Hotspot hotspot;

    @Column(name = "stop_index")
    Integer stopIndex;

    @Column(name = "user_note", columnDefinition = "TEXT")
    String userNote;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;
}
