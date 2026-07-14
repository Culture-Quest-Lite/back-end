package org.sep490.backend.module.content.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.sep490.backend.module.content.entity.enumeration.MediaType;
import org.sep490.backend.module.social.entity.Post;
import org.sep490.backend.module.admin.entity.PartnerInfo;
import org.sep490.backend.module.partner.entity.Voucher;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_info_id")
    PartnerInfo partnerInfo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id")
    Voucher voucher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    Route route;

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

    @Column(name = "display_order")
    Integer displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;
}
