package org.sep490.backend.module.content.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.enumeration.ReviewStatus;
import org.sep490.backend.module.content.entity.enumeration.ReviewTargetType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reviews", indexes = {
        @Index(name = "idx_review_hotspot_status", columnList = "hotspot_id, status"),
        @Index(name = "idx_review_route_status", columnList = "route_id, status"),
        @Index(name = "idx_review_story_status", columnList = "story_id, status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    Long reviewId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotspot_id")
    Hotspot hotspot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    Route route;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id")
    Story story;

    @Column(name = "rating", nullable = false)
    Integer rating;

    @Column(name = "comment", columnDefinition = "TEXT")
    String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    ReviewStatus status;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    List<Media> medias = new ArrayList<>();

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    List<ReviewAction> reviewActions = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;

    public ReviewTargetType getTargetType() {
        if (hotspot != null) {
            return ReviewTargetType.HOTSPOT;
        }
        if (route != null) {
            return ReviewTargetType.ROUTE;
        }
        if (story != null) {
            return ReviewTargetType.STORY;
        }
        return null;
    }

    public Long getTargetId() {
        if (hotspot != null) {
            return hotspot.getHotspotId();
        }
        if (route != null) {
            return route.getRouteId();
        }
        if (story != null) {
            return story.getStoryId();
        }
        return null;
    }
}
