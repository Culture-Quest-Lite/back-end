package org.sep490.backend.module.content.dto.record;

import org.sep490.backend.module.content.entity.enumeration.CultureDecision;

import java.util.List;

public record CultureCheckResult(
        CultureDecision decision,
        CultureVerdict verdict,
        boolean fromLlm
) {
    public static CultureCheckResult rule(CultureDecision decision, double score, String reason, List<String> themes) {
        return new CultureCheckResult(decision, new CultureVerdict(score, themes, reason, List.of()), false);
    }

    public double score() {
        return verdict.safeScore();
    }

    public String reason() {
        return verdict.reason();
    }
}
