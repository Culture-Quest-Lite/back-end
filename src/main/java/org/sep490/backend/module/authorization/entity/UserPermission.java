package org.sep490.backend.module.authorization.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.sep490.backend.module.authentication.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_permissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_permission",
                columnNames = {"user_id", "permission_id"}),
        indexes = @Index(name = "idx_user_permission_user", columnList = "user_id"))
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @Column(nullable = false)
    private boolean granted;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(length = 255)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
