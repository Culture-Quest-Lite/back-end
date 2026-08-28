package org.sep490.backend.module.admin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Cấu hình bán kính vùng check-in do ADMIN đặt.
 * Bảng chỉ giữ duy nhất 1 dòng (singleton), curator không được sửa.
 */
@Entity
@Data
@Table(name = "check_in_radius_config")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CheckInRadiusConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Long configId;

    @Column(name = "min_radius", nullable = false)
    private Integer minRadius;

    @Column(name = "max_radius", nullable = false)
    private Integer maxRadius;

    @Column(name = "default_radius", nullable = false)
    private Integer defaultRadius;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
