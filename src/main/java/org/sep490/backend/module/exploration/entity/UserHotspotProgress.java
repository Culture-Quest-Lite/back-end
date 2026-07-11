package org.sep490.backend.module.exploration.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.Point;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Hotspot;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_hotspot_progress",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "hotspot_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserHotspotProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_progress_id")
    Long userProgressId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @ManyToOne
    @JoinColumn(name = "hotspot_id", nullable = false)
    Hotspot hotspot;

    @Column(name = "location")
    Point location;

    @Column(name = "total_point_earned")
    Integer totalPointEarned;

    @Column(name = "total_xp_earned")
    Integer totalXpEarned;

    @CreationTimestamp
    @Column(name = "first_visited_at", updatable = false)
    LocalDateTime firstVisitedAt;
}
