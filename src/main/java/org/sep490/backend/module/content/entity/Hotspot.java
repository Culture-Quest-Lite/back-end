package org.sep490.backend.module.content.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.enums.ContentStatus;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "hotspots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Hotspot implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hotspot_id")
    Long hotspotId;

    @ManyToMany
    @JoinTable(
            name = "hotspot_categories",
            joinColumns = @JoinColumn(name = "hotspot_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    Set<Category> categories = new HashSet<>();

//    @ManyToOne
//    @JoinColumn(name = "created_by", nullable = false)
//    User createdBy;

    @Column(name = "hotspot_name", nullable = false, length = 100)
    String hotspotName;

    @Column(name = "address", length = 255)
    String address;

    @Column(name = "description", columnDefinition = "TEXT")
    String description;

    @Column(name = "check_in_radius")
    Double checkInRadius;

    @Column(name = "location")
    Point location;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    ContentStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;

    @Column(name = "published_at")
    LocalDateTime publishedAt;


    public void addCategory(Category category) {
        this.categories.add(category);
        category.getHotspots().add(this);
    }

    public void removeCategory(Category category) {
        this.categories.remove(category);
        category.getHotspots().remove(this);
    }
}
