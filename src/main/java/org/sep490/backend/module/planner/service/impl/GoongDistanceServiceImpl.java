package org.sep490.backend.module.planner.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.common.utils.SpatialUtils;
import org.sep490.backend.config.goong.GoongClient;
import org.sep490.backend.config.goong.dto.GoongDistanceMatrixResponse;
import org.sep490.backend.module.planner.dto.record.DistanceMatrixResult;
import org.sep490.backend.module.planner.service.GoongDistanceService;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GoongDistanceServiceImpl implements GoongDistanceService {

    static String VEHICLE = "bike";

    static double FALLBACK_SPEED_MPS = 25_000.0 / 3600.0;

    GoongClient goongClient;

    @Override
    public DistanceMatrixResult getMatrix(List<double[]> points) {
        int n = points.size();
        try {
            GoongDistanceMatrixResponse resp = goongClient.distanceMatrix(points, points, VEHICLE);
            if (resp == null || resp.getRows() == null || resp.getRows().size() != n) {
                return haversineMatrix(points);
            }
            double[][] dist = new double[n][n];
            double[][] dur = new double[n][n];
            for (int i = 0; i < n; i++) {
                var elements = resp.getRows().get(i).getElements();
                if (elements == null || elements.size() != n) {
                    return haversineMatrix(points);
                }
                for (int j = 0; j < n; j++) {
                    var el = elements.get(j);
                    if (el == null || el.getDistance() == null || el.getDuration() == null) {
                        return haversineMatrix(points);
                    }
                    dist[i][j] = el.getDistance().getValue();
                    dur[i][j] = el.getDuration().getValue();
                }
            }
            return new DistanceMatrixResult(dist, dur, false);
        } catch (Exception e) {
            log.warn("[Goong] DistanceMatrix lỗi, fallback Haversine: {}", e.getMessage());
            return haversineMatrix(points);
        }
    }

    private DistanceMatrixResult haversineMatrix(List<double[]> points) {
        int n = points.size();
        double[][] dist = new double[n][n];
        double[][] dur = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                double meters = SpatialUtils.calculateDistanceInMeters(
                        SpatialUtils.fromCoordinates(points.get(i)[1], points.get(i)[0]),
                        SpatialUtils.fromCoordinates(points.get(j)[1], points.get(j)[0]));
                dist[i][j] = meters;
                dur[i][j] = meters / FALLBACK_SPEED_MPS; // giây
            }
        }
        return new DistanceMatrixResult(dist, dur, true);
    }
}
