package org.sep490.backend.common.utils;

import org.locationtech.jts.geom.Point;

public class SpatialUtils {
    private static final double EARTH_RADIUS_METERS = 6371000;

    public static double calculateDistanceInMeters(Point p1, Point p2) {
        if (p1 == null || p2 == null) return 0.0;

        double lat1 = p1.getY();
        double lon1 = p1.getX();
        double lat2 = p2.getY();
        double lon2 = p2.getX();

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }
}
