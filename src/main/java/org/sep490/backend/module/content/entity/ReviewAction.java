package org.sep490.backend.module.content.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.enumeration.ReviewActionType;

import java.time.LocalDateTime;

@Entity
@Table(name = "review_actions", uniqueConstraints = {
        //Mỗi người chỉ được thích một đánh giá đúng một lần
        @UniqueConstraint(name = "uk_review_action_user", columnNames = {"review_id", "user_id", "action_type"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReviewAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_action_id")
    Long reviewActionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    Review review;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    ReviewActionType actionType;

    @Column(name = "comment", columnDefinition = "TEXT")
    String comment;

    @Column(name = "is_report_resolved")
    Boolean isReportResolved;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;
}
