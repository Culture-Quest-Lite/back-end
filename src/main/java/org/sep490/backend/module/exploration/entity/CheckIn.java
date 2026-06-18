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
@Table(name = "check_ins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "check_in_id")
    Long checkInId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @ManyToOne
    @JoinColumn(name = "hotspot_id", nullable = false)
    Hotspot hotspot;

    @ManyToOne
    @JoinColumn(name = "user_route_progress_id")
    UserRouteProgress userRouteProgress;

    @Column(name = "location", nullable = false)
    Point checkInLocation;

    @Column(name = "distance_to_hotspot", nullable = false)
    Double distanceToHotspot; // meter

    @Column(name = "point_earned", nullable = false)
    Long pointEarned;

    @Column(name = "xp_earned", nullable = false)
    Long xpEarned;

    @CreationTimestamp
    @Column(name = "check_in_at", updatable = false)
    LocalDateTime checkInAt;
}
