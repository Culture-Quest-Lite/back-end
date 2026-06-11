package org.sep490.backend.module.content.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.sep490.backend.module.content.enums.MediaType;

import java.time.LocalDateTime;

@Entity
@Table(name = "medias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Media {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "media_id")
    Long mediaId;

    @ManyToOne
    @JoinColumn(name = "story_id")
    Story story;

    @ManyToOne
    @JoinColumn(name = "hotspot_id")
    Hotspot hotspot;

    // post_id

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type")
    MediaType mediaType;

    @Column(name = "mime_type", length = 30)
    String mimeType;

    @Column(name = "file_url")
    String fileUrl;

    @Column(name = "file_name", length = 50)
    String fileName;

    @Column(name = "file_size")
    Double fileSize; // MB

    @Column(name = "duration")
    Double duration; // s

    @Column(name = "display_order")
    Integer displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;
}
