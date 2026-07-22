package org.sep490.backend.module.authentication.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.sep490.backend.module.admin.entity.enumeration.AuditAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
        //Index cho cột thời gian vì danh sách log luôn sắp xếp mới nhất trước
        @Index(name = "idx_audit_created_at", columnList = "created_at"),
        //Composite index để lọc log theo người thực hiện và loại hành động
        @Index(name = "idx_audit_user_action", columnList = "user_id, action")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    @Column(name = "table_name", length = 50)
    private String tableName;

    @Column(name = "endpoint", length = 255)
    private String endpoint;

    @Column(name = "record_id")
    private String recordId;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
