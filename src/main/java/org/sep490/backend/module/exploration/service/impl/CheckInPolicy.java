package org.sep490.backend.module.exploration.service.impl;

public final class CheckInPolicy {

    public static final int DEFAULT_RADIUS_METERS = 50;

    public static final int MIN_RADIUS_METERS = 20;
    public static final int MAX_RADIUS_METERS = 5000;

    public static final double MAX_GPS_ACCURACY_TOLERANCE_METERS = 100.0;

    private CheckInPolicy() {
    }

    public static double toleranceFrom(Double accuracy) {
        if (accuracy == null || accuracy <= 0) {
            return 0.0;
        }
        return Math.min(accuracy, MAX_GPS_ACCURACY_TOLERANCE_METERS);
    }

    public static int effectiveRadius(Integer checkInRadius) {
        return checkInRadius != null ? checkInRadius : DEFAULT_RADIUS_METERS;
    }
}
