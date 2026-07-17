package org.sep490.backend.module.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.locationtech.jts.geom.Point;
import org.sep490.backend.module.admin.entity.enumeration.PartnerInfoStatus;
import org.sep490.backend.module.authentication.entity.User;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import org.sep490.backend.module.content.entity.Media;

@Entity
@Table(name = "partner_info")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PartnerInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "partner_info_id")
    Long partnerInfoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Column(name = "shop_name", nullable = false, length = 100)
    String shopName;

    @Column(name = "shop_email", nullable = false, unique = true, length = 100)
    String shopEmail;

    @Column(name = "address", length = 255)
    String address;

    @Column(columnDefinition = "geometry(Point, 4326)")
    Point location;

    @Column(name = "document_url", length = 255)
    String documentUrl;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    PartnerInfoStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;

    @OneToMany(mappedBy = "partnerInfo", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    List<Media> medias = new ArrayList<>();
}
