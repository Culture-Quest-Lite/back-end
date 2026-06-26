package org.sep490.backend.module.gamification.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.gamification.entity.enumeration.ActionType;

import java.time.LocalDateTime;

@Entity
@Table(name = "xp_histories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class XpHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "xp_amount", nullable = false)
    private Long xpAmount;

    @Column(name = "balance_after")
    private Long balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private ActionType source;

    @Column(name = "reference_id")
    private Long referenceId; // Lưu ID của Hotspot/Route/Post tương ứng

    @Column(name = "description")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
