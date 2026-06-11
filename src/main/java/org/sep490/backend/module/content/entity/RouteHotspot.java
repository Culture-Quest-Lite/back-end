package org.sep490.backend.module.content.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "route_hotspots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RouteHotspot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "route_hotspot_id")
    Long routeHotspotId;

    @ManyToOne
    @JoinColumn(name = "hotspot_id", nullable = false)
    Hotspot hotspot;

    @ManyToOne
    @JoinColumn(name = "route_id", nullable = false)
    Route route;

    @Column(name = "index")
    Integer index;

    @Column(name = "distance_to_next")
    Double distanceToNext;
}
