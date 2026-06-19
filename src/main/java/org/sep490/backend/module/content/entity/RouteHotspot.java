package org.sep490.backend.module.content.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "route_hotspots", indexes = {
        //Đánh composite index cho 2 khóa ngoại để tăng tốc độ join bảng
        @Index(name = "idx_route_hotspot_composite", columnList = "route_id, hotspot_id")
})
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
