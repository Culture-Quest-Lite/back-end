package org.sep490.backend.module.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sep490.backend.module.user.entity.enumeration.LevelStatus;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LevelResponse {
    private Long levelId;
    private String name;
    private Integer requiredXp;
    private String description;
    private LevelStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
