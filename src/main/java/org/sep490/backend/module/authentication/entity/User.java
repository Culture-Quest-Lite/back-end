package org.sep490.backend.module.authentication.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.sep490.backend.module.partner.entity.Voucher;
import org.sep490.backend.module.user.entity.Level;
import org.sep490.backend.module.user.entity.enumeration.UserRole;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "users", indexes = {
        //Single index cho cột status để quản lý danh sách User
        @Index(name = "idx_user_status", columnList = "status"),
        //Composite index để query tìm User theo Role và Status
        @Index(name = "idx_user_role_status", columnList = "role, status")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "keycloak_user_id", unique = true, nullable = false, length = 64)
    private String keycloakUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id", nullable = true)
    private Level level;

    @OneToMany(mappedBy = "partner", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Voucher> vouchers = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AuditLog> auditLogs = new ArrayList<>();

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "background_url")
    private String backgroundUrl;

    @Builder.Default
    @Column(name = "total_xp")
    private Integer totalXp = 0;

    @Builder.Default
    @Column(name = "total_points")
    private Integer totalPoints = 0;

    @Builder.Default
    @Column(name = "auto_play_audio")
    private Boolean autoPlayAudio = true;

    @Builder.Default
    @Column(name = "is_premium")
    private Boolean isPremium = false;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 50)
    private UserRole role;

    @Column(name = "fcm_token")
    private String fcmToken;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
