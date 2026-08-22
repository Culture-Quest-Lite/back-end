package org.sep490.backend.module.authorization.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "permissions",
        indexes = @Index(name = "idx_permission_code", columnList = "code"))
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id")
    Long permissionId;

    @Column(name = "code", nullable = false, length = 100, unique = true)
    String code;

    @Column(name = "group_name", length = 50)
    String groupName;

    @Column(length = 255)
    String description;

    @Builder.Default
    @Column(nullable = false)
    boolean active = true;
}
