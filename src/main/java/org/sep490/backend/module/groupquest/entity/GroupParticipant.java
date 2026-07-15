package org.sep490.backend.module.groupquest.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupParticipantAction;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupRole;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "group_participants", indexes = {
        @Index(name = "idx_unique_user_group", columnList = "user_id, group_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GroupParticipant {

    @Id
    @Column(name = "group_participant_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long groupParticipantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    Group group;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    GroupRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "action")
    GroupParticipantAction action;

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
