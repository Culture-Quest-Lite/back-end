package org.sep490.backend.module.content.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "stories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Story {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "story_id")
    Long storyId;

    @ManyToOne
    @JoinColumn(name = "tag_id", nullable = false)
    Tag tag;

    @ManyToOne
    @JoinColumn(name = "hotspot_id", nullable = true)
    Hotspot hotspot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = true)
    Route route;

    @ManyToOne
    @JoinColumn(name = "created_by", nullable = false)
    User createdBy;

    @Column(name = "order_index", nullable = true)
    Integer orderIndex;

    @Column(name = "title", nullable = false, length = 100)
    String title;

    @Column(name = "content", columnDefinition = "TEXT")
    String content;

    @Column(name = "distance_to_next", nullable = true)
    Double distanceToNext; // km

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    ContentStatus status;

    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    List<Media> medias = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;
}
