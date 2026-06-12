package org.sep490.backend.module.gamification.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GamificationResult {
    private Integer xpGained;
    private Integer pointsGained;
    private Integer currentTotalXp;
    private Integer currentTotalPoints;
    private String currentLevelName;
    private boolean leveledUp;
}
