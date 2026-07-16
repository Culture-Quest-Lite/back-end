package org.sep490.backend.module.planner.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.utils.FormatUtils;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.planner.dto.record.DistanceMatrixResult;
import org.sep490.backend.module.planner.dto.request.OptimizeRouteRequest;
import org.sep490.backend.module.planner.dto.response.OptimizedRouteResponse;
import org.sep490.backend.module.planner.dto.response.OptimizedStopResponse;
import org.sep490.backend.module.planner.entity.enumeration.OptimizeCriterion;
import org.sep490.backend.module.planner.service.GoongDistanceService;
import org.sep490.backend.module.planner.service.RouteOptimizationService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RouteOptimizationServiceImpl implements RouteOptimizationService {

    static double URGENCY_BONUS_SECONDS = 3600.0;
    static long   URGENCY_THRESHOLD_MINUTES = 90;

    HotspotService hotspotService;
    GoongDistanceService goongDistanceService;

    @Override
    public OptimizedRouteResponse optimize(OptimizeRouteRequest request) {
        List<Long> ids = request.getHotspotIds();
        if (ids == null || ids.size() < 2) {
            throw new BusinessException("Cần ít nhất 2 điểm dừng để tối ưu");
        }

        List<Hotspot> hotspots = new ArrayList<>();
        for (Long id : ids) hotspots.add(hotspotService.getById(id));

        boolean hasStart = request.getStartLatitude() != null && request.getStartLongitude() != null;

        List<double[]> points = new ArrayList<>();
        if (hasStart) points.add(new double[]{request.getStartLatitude(), request.getStartLongitude()});
        for (Hotspot h : hotspots) {
            points.add(new double[]{h.getLocation().getY(), h.getLocation().getX()});
        }

        DistanceMatrixResult matrix = goongDistanceService.getMatrix(points);

        OptimizeCriterion criterion = request.getCriterion() == null ? OptimizeCriterion.TIME : request.getCriterion();
        double[][] cost = criterion == OptimizeCriterion.DISTANCE ? matrix.distanceMeters() : matrix.durationSeconds();

        int n = points.size();
        int startIndex = 0;

        List<Integer> order;
        if (hasStart && request.getStartTime() != null) {
            order = nearestNeighborWithClosing(cost, matrix.durationSeconds(), startIndex, n,
                    request.getStartTime(), hasStart, hotspots);
        } else {
            order = nearestNeighbor(cost, startIndex, n);
            order = twoOpt(order, cost, hasStart);
        }

        return buildResponse(order, hasStart, hotspots, matrix, criterion, request);
    }

    private List<Integer> nearestNeighbor(double[][] cost, int start, int n) {
        boolean[] visited = new boolean[n];
        List<Integer> order = new ArrayList<>();
        int current = start;
        visited[current] = true;
        order.add(current);
        for (int step = 1; step < n; step++) {
            int next = -1;
            double best = Double.MAX_VALUE;
            for (int j = 0; j < n; j++) {
                if (!visited[j] && cost[current][j] < best) {
                    best = cost[current][j];
                    next = j;
                }
            }
            if (next == -1) break;
            visited[next] = true;
            order.add(next);
            current = next;
        }
        return order;
    }

    private List<Integer> nearestNeighborWithClosing(double[][] cost, double[][] durSec, int start, int n,
                                                     LocalTime startTime, boolean hasStart, List<Hotspot> hotspots) {
        boolean[] visited = new boolean[n];
        List<Integer> order = new ArrayList<>();
        int current = start;
        visited[current] = true;
        order.add(current);
        LocalTime clock = startTime;

        for (int step = 1; step < n; step++) {
            int next = -1;
            double best = Double.MAX_VALUE;
            for (int j = 0; j < n; j++) {
                if (visited[j]) continue;
                double c = cost[current][j];
                Hotspot h = hotspotAt(j, hasStart, hotspots);
                if (h != null && h.getClosingTime() != null && clock != null) {
                    LocalTime projArrival = clock.plusSeconds((long) durSec[current][j]);
                    long slack = Duration.between(projArrival, h.getClosingTime()).toMinutes();
                    if (slack < URGENCY_THRESHOLD_MINUTES) {
                        c -= URGENCY_BONUS_SECONDS;
                    }
                }
                if (c < best) { best = c; next = j; }
            }
            if (next == -1) break;
            visited[next] = true;
            order.add(next);
            if (clock != null) {
                clock = clock.plusSeconds((long) durSec[current][next]);
                Hotspot h = hotspotAt(next, hasStart, hotspots);
                if (h != null && h.getEstimatedDurationMin() != null) {
                    clock = clock.plusMinutes(h.getEstimatedDurationMin());
                }
            }
            current = next;
        }
        return order;
    }

    private List<Integer> twoOpt(List<Integer> order, double[][] cost, boolean startFixed) {
        int n = order.size();
        double bestCost = pathCost(order, cost);
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = startFixed ? 1 : 0; i < n - 1; i++) {
                for (int k = i + 1; k < n; k++) {
                    List<Integer> candidate = twoOptSwap(order, i, k);
                    double c = pathCost(candidate, cost);
                    if (c + 1e-9 < bestCost) {
                        order = candidate;
                        bestCost = c;
                        improved = true;
                    }
                }
            }
        }
        return order;
    }

    private List<Integer> twoOptSwap(List<Integer> order, int i, int k) {
        List<Integer> result = new ArrayList<>(order.subList(0, i));
        List<Integer> middle = new ArrayList<>(order.subList(i, k + 1));
        Collections.reverse(middle);
        result.addAll(middle);
        result.addAll(order.subList(k + 1, order.size()));
        return result;
    }

    private double pathCost(List<Integer> order, double[][] cost) {
        double total = 0;
        for (int t = 0; t < order.size() - 1; t++) {
            total += cost[order.get(t)][order.get(t + 1)];
        }
        return total;
    }

    private OptimizedRouteResponse buildResponse(List<Integer> order, boolean hasStart,
                                                 List<Hotspot> hotspots, DistanceMatrixResult matrix,
                                                 OptimizeCriterion criterion, OptimizeRouteRequest request) {
        double[][] distM = matrix.distanceMeters();
        double[][] durS = matrix.durationSeconds();

        List<OptimizedStopResponse> stops = new ArrayList<>();
        double totalDistanceKm = 0;
        double totalMinutes = 0;
        LocalTime clock = request.getStartTime();

        int stopIndex = 1;
        for (int p = 0; p < order.size(); p++) {
            int node = order.get(p);
            Hotspot h = hotspotAt(node, hasStart, hotspots);

            LocalTime arrival = null;
            if (clock != null) {
                if (p > 0) clock = clock.plusSeconds((long) durS[order.get(p - 1)][node]);
                arrival = clock;
            }

            double distToNext = 0, travelMin = 0;
            if (p < order.size() - 1) {
                int nextNode = order.get(p + 1);
                distToNext = distM[node][nextNode] / 1000.0; // ma trận Goong trả mét -> km
                travelMin = durS[node][nextNode] / 60.0;
                totalDistanceKm += distToNext;
                totalMinutes += travelMin;
            }

            if (h == null) {
                continue;
            }

            boolean closingWarning = h.getClosingTime() != null && arrival != null
                    && arrival.isAfter(h.getClosingTime());

            stops.add(OptimizedStopResponse.builder()
                    .hotspotId(h.getHotspotId())
                    .index(stopIndex++)
                    .hotspotName(h.getHotspotName())
                    .latitude(h.getLocation().getY())
                    .longitude(h.getLocation().getX())
                    .distanceToNext(FormatUtils.round2(distToNext))
                    .travelTimeToNext(FormatUtils.round2(travelMin))
                    .travelTimeToNextText(FormatUtils.humanizeMinutes(travelMin))
                    .estimatedArrivalTime(arrival)
                    .closingWarning(closingWarning)
                    .build());

            if (h.getEstimatedDurationMin() != null) {
                totalMinutes += h.getEstimatedDurationMin();
                if (clock != null) clock = clock.plusMinutes(h.getEstimatedDurationMin());
            }
        }

        return OptimizedRouteResponse.builder()
                .stops(stops)
                .totalDistance(FormatUtils.round2(totalDistanceKm))
                .totalEstimatedTime(FormatUtils.round2(totalMinutes))
                .totalEstimatedTimeText(FormatUtils.humanizeMinutes(totalMinutes))
                .criterion(criterion)
                .usedFallback(matrix.fromFallback())
                .build();
    }

    private Hotspot hotspotAt(int node, boolean hasStart, List<Hotspot> hotspots) {
        int hIdx = hasStart ? node - 1 : node;
        if (hIdx < 0 || hIdx >= hotspots.size()) return null;
        return hotspots.get(hIdx);
    }
}
