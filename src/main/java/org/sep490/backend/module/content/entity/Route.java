package org.sep490.backend.module.content.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.sep490.backend.module.content.entity.enumeration.RouteDifficulty;
import org.sep490.backend.module.content.entity.enumeration.RouteStatus;
import org.sep490.backend.module.content.entity.enumeration.RouteType;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "routes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "route_id")
    Long routeId;

    @ManyToOne
    @JoinColumn(name = "created_by", nullable = false)
    User createdBy;

    @ManyToMany
    @JoinTable(
            name = "route_tags",
            joinColumns = @JoinColumn(name = "route_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    Set<Tag> tags = new HashSet<>();

    @Column(name = "route_name", nullable = false, length = 100)
    String routeName;

    @Column(name = "description", columnDefinition = "TEXT")
    String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false)
    RouteDifficulty difficulty;

    @Column(name = "XP", nullable = false)
    Long xp;

    @Column(name = "point", nullable = false)
    Long point;

    @Column(name = "estimate_time", nullable = false)
    Double estimateTime; // minutes

    @Column(name = "total_distance", nullable = false)
    Double totalDistance;

    @Builder.Default
    @Column(name = "total_check_ins", nullable = false)
    Integer totalCheckIns = 0;

    @Column(name = "is_locked")
    Boolean isLocked;

    // to reduce query
    @Column(name = "total_stops", nullable = false)
    Integer totalStops;

    @Column(name = "share_token")
    String shareToken;

    @Column(name = "share_expired_at")
    LocalDateTime shareExpiredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    RouteType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    RouteStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;

    @Column(name = "published_at")
    LocalDateTime publishedAt;
}
