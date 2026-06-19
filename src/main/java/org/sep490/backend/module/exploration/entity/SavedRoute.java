package org.sep490.backend.module.exploration.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Route;

import java.time.LocalDateTime;

@Entity
@Table(name = "saved_routes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SavedRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "saved_route_id")
    Long savedRouteId;

    @ManyToOne
    @JoinColumn(name = "route_id", nullable = false)
    Route route;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @CreationTimestamp
    @Column(name = "saved_at", nullable = false)
    LocalDateTime savedAt;
}
