package org.sep490.backend.module.planner.dto.record;

public record DistanceMatrixResult(
        double[][] distanceMeters,
        double[][] durationSeconds,
        boolean fromFallback
){}