package org.sep490.backend.module.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LevelProgressResponse {
    private Long levelProgressId;
    private Long levelId;
    private Long userId;
    private String levelName;
    private Integer requiredXp;
    private Integer xpAtUnlock;
    private LocalDateTime unlockedAt;
}
