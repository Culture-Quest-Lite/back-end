package org.sep490.backend.module.content.dto.record;

import java.util.List;

public record CultureVerdict(
        Double score,
        List<String> themes,
        String reason,
        List<String> suggestions
) {
    public double safeScore() {
        return score == null ? 0d : Math.max(0d, Math.min(1d, score));
    }

    public List<String> safeThemes() {
        return themes == null ? List.of() : themes;
    }

    public List<String> safeSuggestions() {
        return suggestions == null ? List.of() : suggestions;
    }
}
