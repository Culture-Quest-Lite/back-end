package org.sep490.backend.module.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.sep490.backend.module.authentication.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_follows", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"follower_id", "following_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFollow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "following_id", nullable = false)
    private User following;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
