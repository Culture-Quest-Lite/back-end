package org.sep490.backend.module.social.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Media;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.social.entity.enumeration.PostStatus;
import org.sep490.backend.module.social.entity.enumeration.PostVisibility;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "posts", indexes = {
        //Tìm bài viết theo tác giả
        @Index(name = "idx_post_user", columnList = "user_id"),
        //Lọc theo trạng thái hiển thị và sắp xếp bài viết mới nhất lên đầu
        @Index(name = "idx_post_feed_flow", columnList = "status, visibility, created_at")
})
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    Long postId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Column(name = "content", columnDefinition = "TEXT")
    String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility")
    PostVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    PostStatus status;

    @Column(name = "reject_reason", nullable = true)
    String reason;

    @Column(name = "moderate_by")
    Long moderateBy;

    @Column(name = "moderate_at")
    LocalDateTime moderateAt;

    @Column(name = "is_tagged_hotspot")
    Boolean isTaggedHotspot;

    @Column(name = "is_tagged_route")
    Boolean isTaggedRoute;

    @ManyToMany
    @JoinTable(
            name = "post_hotspot",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "hotspot_id",
            nullable = true)
    )
    @Builder.Default
    Set<Hotspot> taggedHotspots = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "post_route",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "route_id",
            nullable = true)
    )
    @Builder.Default
    Set<Route> taggedRoutes = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "post_tags",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id",
                    nullable = true)
    )
    @Builder.Default
    Set<Tag> tags = new HashSet<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    List<Media> medias = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;
}
