package org.sep490.backend.module.exploration.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.exploration.entity.enumuration.ProgressStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_route_progresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserRouteProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_route_progress_id")
    Long userRouteProgressId;

    @ManyToOne
    @JoinColumn(name = "route_id", nullable = false)
    Route route;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Column(name = "total_stops", nullable = false)
    Integer totalStops;

    @Column(name = "completed_stops", nullable = false)
    Integer completedStops;

    @Column(name = "progress_percentage", nullable = false)
    Double progressPercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    ProgressStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime startedAt;

    @Column(name = "completed_at")
    LocalDateTime completedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;
}
