package org.sep490.backend.common.utils;

import org.sep490.backend.module.content.entity.enumeration.RouteDifficulty;

public class RewardUtils {

    public static long calculateXpOrPoint(RouteDifficulty difficulty, int size, boolean isXp) {
        double rate = 1.0;
        switch (difficulty) {
            case EASY:
                rate = 1.15;
                break;
            case MEDIUM:
                rate = 1.2;
                break;
            case HARD:
                rate = 1.25;
                break;
            default:
                rate = 1.0;
        }

        return isXp
                ? Math.round((size * 100 * rate))
                : Math.round((size * 10 * rate));
    }
}
