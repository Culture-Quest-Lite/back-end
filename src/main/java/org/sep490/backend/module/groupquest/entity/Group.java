package org.sep490.backend.module.groupquest.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "groups", indexes = {
        @Index(name = "idx_group_share_token", columnList = "share_token", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Group {

    @Id
    @Column(name = "group_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long groupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    User createdBy;

    @Column(name = "group_name", length = 255, nullable = false)
    String groupName;

    @Column(name = "total_members")
    Integer totalMembers;

    @Column(name = "share_token", length = 10)
    String shareToken;

    @Column(name = "expired_at")
    LocalDateTime expireAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    GroupStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;
}
