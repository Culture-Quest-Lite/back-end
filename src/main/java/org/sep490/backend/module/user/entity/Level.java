package org.sep490.backend.module.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.user.entity.enumeration.LevelStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "levels")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Level {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "level_id")
    private Long levelId;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "req_xp", nullable = false)
    private Integer requiredXp;

    @Column(length = 255)
    private String description;

    @OneToMany(mappedBy = "level", fetch = FetchType.LAZY)
    private List<User> users = new ArrayList<>();
    
    @Enumerated(EnumType.STRING)
    private LevelStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
