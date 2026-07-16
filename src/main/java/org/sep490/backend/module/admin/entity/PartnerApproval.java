package org.sep490.backend.module.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.admin.entity.enumeration.PartnerApprovalStatus;
import org.sep490.backend.module.authentication.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "partner_approval")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PartnerApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "partner_approval_id")
    Long partnerApprovalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_info_id", nullable = false)
    PartnerInfo partnerInfo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    User reviewer;

    @Column(name = "deny_reason", columnDefinition = "TEXT")
    String denyReason;

    @Column(name = "approval_status")
    @Enumerated(EnumType.STRING)
    PartnerApprovalStatus approvalStatus;

    @Column(name = "reviewed_at")
    LocalDateTime reviewedAt;
}
