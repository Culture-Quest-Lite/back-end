package org.sep490.backend.module.planner.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.planner.dto.record.DistanceMatrixResult;
import org.sep490.backend.module.planner.dto.request.OptimizeRouteRequest;
import org.sep490.backend.module.planner.dto.response.OptimizedRouteResponse;
import org.sep490.backend.module.planner.dto.response.OptimizedStopResponse;
import org.sep490.backend.module.planner.entity.enumeration.OptimizeCriterion;
import org.sep490.backend.module.planner.service.GoongDistanceService;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho THUẬT TOÁN TỐI ƯU LỘ TRÌNH (nearest neighbor + 2-opt).
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RouteOptimizationServiceImplTest {

    @Mock private HotspotService hotspotService;
    @Mock private GoongDistanceService goongDistanceService;

    @InjectMocks private RouteOptimizationServiceImpl routeOptimizationService;

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    /** Hotspot với toạ độ (lon, lat) theo chuẩn JTS Point(x = lon, y = lat). */
    private static Hotspot hotspot(Long id, String name, double lon, double lat,
                                   Long durationMin, LocalTime closingTime) {
        Hotspot hotspot = new Hotspot();
        hotspot.setHotspotId(id);
        hotspot.setHotspotName(name);
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
        hotspot.setLocation(point);
        hotspot.setEstimatedDurationMin(durationMin);
        hotspot.setClosingTime(closingTime);
        return hotspot;
    }

    private static OptimizeRouteRequest request(List<Long> hotspotIds, OptimizeCriterion criterion) {
        OptimizeRouteRequest request = new OptimizeRouteRequest();
        request.setHotspotIds(hotspotIds);
        request.setCriterion(criterion);
        return request;
    }

    // =====================================================================
    // Function: optimize
    // =====================================================================
    @Nested
    @DisplayName("optimize")
    class OptimizeTest {

        // UTCID01 - Abnormal: danh sách điểm dừng = null
        @Test
        void optimize_nullHotspotIds_throwsNeedTwoStops() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeOptimizationService.optimize(request(null, OptimizeCriterion.TIME)));

            assertEquals("Cần ít nhất 2 điểm dừng để tối ưu", ex.getMessage());
            verifyNoInteractions(goongDistanceService);
        }

        // UTCID02 - Boundary: chỉ có 1 điểm dừng -> không đủ để tối ưu
        @Test
        void optimize_singleHotspot_throwsNeedTwoStops() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeOptimizationService.optimize(
                            request(List.of(1L), OptimizeCriterion.TIME)));

            assertEquals("Cần ít nhất 2 điểm dừng để tối ưu", ex.getMessage());
        }

        // UTCID03 - Boundary: đúng 2 điểm dừng -> hợp lệ, trả về đủ 2 chặng
        @Test
        void optimize_exactlyTwoHotspots_returnsTwoStops() {
            Hotspot a = hotspot(1L, "Chợ Đà Lạt", 108.4380, 11.9404, null, null);
            Hotspot b = hotspot(2L, "Hồ Xuân Hương", 108.4419, 11.9416, null, null);
            when(hotspotService.getById(1L)).thenReturn(a);
            when(hotspotService.getById(2L)).thenReturn(b);
            when(goongDistanceService.getMatrix(anyList())).thenReturn(new DistanceMatrixResult(
                    new double[][]{{0, 1000}, {1000, 0}},
                    new double[][]{{0, 300}, {300, 0}},
                    false));

            OptimizedRouteResponse result = routeOptimizationService.optimize(
                    request(List.of(1L, 2L), OptimizeCriterion.TIME));

            assertEquals(2, result.getStops().size());
            assertEquals(1.0, result.getTotalDistance());   // 1000m -> 1.0km
            assertEquals(5.0, result.getTotalEstimatedTime()); // 300s -> 5 phút
            assertFalse(result.getUsedFallback());
        }

        // UTCID04 - Normal: 3 điểm, thứ tự nhập sai -> thuật toán sắp lại theo chi phí nhỏ nhất
        @Test
        void optimize_threeHotspots_reordersByLowestCost() {
            when(hotspotService.getById(1L))
                    .thenReturn(hotspot(1L, "Điểm A", 108.4380, 11.9404, null, null));
            when(hotspotService.getById(2L))
                    .thenReturn(hotspot(2L, "Điểm B", 108.4500, 11.9500, null, null));
            when(hotspotService.getById(3L))
                    .thenReturn(hotspot(3L, "Điểm C", 108.4400, 11.9410, null, null));

            // A gần C (100s) hơn B (900s) -> lộ trình tối ưu phải là A -> C -> B
            double[][] duration = {
                    {0, 900, 100},
                    {900, 0, 200},
                    {100, 200, 0}
            };
            when(goongDistanceService.getMatrix(anyList())).thenReturn(
                    new DistanceMatrixResult(duration, duration, false));

            OptimizedRouteResponse result = routeOptimizationService.optimize(
                    request(List.of(1L, 2L, 3L), OptimizeCriterion.TIME));

            List<Long> visitOrder = result.getStops().stream()
                    .map(OptimizedStopResponse::getHotspotId).toList();
            assertEquals(List.of(1L, 3L, 2L), visitOrder);
            assertEquals(1, result.getStops().get(0).getIndex());
            assertEquals(3, result.getStops().get(2).getIndex());
        }

        // UTCID05 - Normal: tiêu chí DISTANCE -> dùng ma trận khoảng cách thay vì thời gian
        @Test
        void optimize_distanceCriterion_usesDistanceMatrix() {
            when(hotspotService.getById(1L))
                    .thenReturn(hotspot(1L, "Điểm A", 108.4380, 11.9404, null, null));
            when(hotspotService.getById(2L))
                    .thenReturn(hotspot(2L, "Điểm B", 108.4500, 11.9500, null, null));
            when(hotspotService.getById(3L))
                    .thenReturn(hotspot(3L, "Điểm C", 108.4400, 11.9410, null, null));

            // Theo QUÃNG ĐƯỜNG: A gần B nhất. Theo THỜI GIAN thì ngược lại.
            double[][] distance = {
                    {0, 100, 900},
                    {100, 0, 200},
                    {900, 200, 0}
            };
            double[][] duration = {
                    {0, 900, 100},
                    {900, 0, 200},
                    {100, 200, 0}
            };
            when(goongDistanceService.getMatrix(anyList())).thenReturn(
                    new DistanceMatrixResult(distance, duration, false));

            OptimizedRouteResponse result = routeOptimizationService.optimize(
                    request(List.of(1L, 2L, 3L), OptimizeCriterion.DISTANCE));

            List<Long> visitOrder = result.getStops().stream()
                    .map(OptimizedStopResponse::getHotspotId).toList();
            assertEquals(List.of(1L, 2L, 3L), visitOrder);
            assertEquals(OptimizeCriterion.DISTANCE, result.getCriterion());
        }

        // UTCID06 - Boundary: criterion = null -> mặc định tối ưu theo THỜI GIAN
        @Test
        void optimize_nullCriterion_defaultsToTime() {
            when(hotspotService.getById(anyLong()))
                    .thenReturn(hotspot(1L, "Điểm A", 108.4380, 11.9404, null, null));
            when(goongDistanceService.getMatrix(anyList())).thenReturn(new DistanceMatrixResult(
                    new double[][]{{0, 1000}, {1000, 0}},
                    new double[][]{{0, 300}, {300, 0}},
                    false));

            OptimizedRouteResponse result = routeOptimizationService.optimize(
                    request(List.of(1L, 2L), null));

            assertEquals(OptimizeCriterion.TIME, result.getCriterion());
        }

        // UTCID07 - Normal: có điểm xuất phát -> điểm xuất phát không nằm trong danh sách stops
        @Test
        void optimize_withStartPoint_startIsNotIncludedInStops() {
            when(hotspotService.getById(1L))
                    .thenReturn(hotspot(1L, "Điểm A", 108.4380, 11.9404, null, null));
            when(hotspotService.getById(2L))
                    .thenReturn(hotspot(2L, "Điểm B", 108.4500, 11.9500, null, null));

            // 3 node: [0] = điểm xuất phát, [1] = A, [2] = B
            double[][] matrix = {
                    {0, 500, 800},
                    {500, 0, 300},
                    {800, 300, 0}
            };
            when(goongDistanceService.getMatrix(anyList())).thenReturn(
                    new DistanceMatrixResult(matrix, matrix, false));

            OptimizeRouteRequest request = request(List.of(1L, 2L), OptimizeCriterion.TIME);
            request.setStartLatitude(11.9400);
            request.setStartLongitude(108.4370);

            OptimizedRouteResponse result = routeOptimizationService.optimize(request);

            assertEquals(2, result.getStops().size());
            assertEquals(List.of(1L, 2L),
                    result.getStops().stream().map(OptimizedStopResponse::getHotspotId).toList());
        }

        // UTCID08 - Normal: có giờ xuất phát -> tính được giờ đến dự kiến cho từng điểm
        @Test
        void optimize_withStartTime_computesEstimatedArrivalTimes() {
            when(hotspotService.getById(1L))
                    .thenReturn(hotspot(1L, "Điểm A", 108.4380, 11.9404, 30L, null));
            when(hotspotService.getById(2L))
                    .thenReturn(hotspot(2L, "Điểm B", 108.4500, 11.9500, null, null));

            double[][] matrix = {
                    {0, 600, 1800},     // xuất phát -> A mất 600s = 10 phút
                    {600, 0, 900},      // A -> B mất 900s = 15 phút
                    {1800, 900, 0}
            };
            when(goongDistanceService.getMatrix(anyList())).thenReturn(
                    new DistanceMatrixResult(matrix, matrix, false));

            OptimizeRouteRequest request = request(List.of(1L, 2L), OptimizeCriterion.TIME);
            request.setStartLatitude(11.9400);
            request.setStartLongitude(108.4370);
            request.setStartTime(LocalTime.of(8, 0));

            OptimizedRouteResponse result = routeOptimizationService.optimize(request);

            // 08:00 + 10 phút di chuyển = 08:10 tới điểm A
            assertEquals(LocalTime.of(8, 10), result.getStops().get(0).getEstimatedArrivalTime());
            // 08:10 + 30 phút tham quan + 15 phút di chuyển = 08:55 tới điểm B
            assertEquals(LocalTime.of(8, 55), result.getStops().get(1).getEstimatedArrivalTime());
        }

        // UTCID09 - Abnormal: đến nơi sau giờ đóng cửa -> cảnh báo closingWarning
        @Test
        void optimize_arrivalAfterClosingTime_setsClosingWarning() {
            when(hotspotService.getById(1L))
                    .thenReturn(hotspot(1L, "Bảo tàng", 108.4380, 11.9404, null, LocalTime.of(17, 0)));
            when(hotspotService.getById(2L))
                    .thenReturn(hotspot(2L, "Điểm B", 108.4500, 11.9500, null, null));

            double[][] matrix = {
                    {0, 3600, 7200},    // xuất phát -> Bảo tàng mất 1 giờ
                    {3600, 0, 900},
                    {7200, 900, 0}
            };
            when(goongDistanceService.getMatrix(anyList())).thenReturn(
                    new DistanceMatrixResult(matrix, matrix, false));

            OptimizeRouteRequest request = request(List.of(1L, 2L), OptimizeCriterion.TIME);
            request.setStartLatitude(11.9400);
            request.setStartLongitude(108.4370);
            request.setStartTime(LocalTime.of(17, 30));   // xuất phát muộn

            OptimizedRouteResponse result = routeOptimizationService.optimize(request);

            OptimizedStopResponse museum = result.getStops().stream()
                    .filter(stop -> stop.getHotspotId().equals(1L))
                    .findFirst().orElseThrow();
            assertEquals(LocalTime.of(18, 30), museum.getEstimatedArrivalTime());
            assertTrue(museum.getClosingWarning());
        }

        // UTCID10 - Boundary: Goong lỗi phải dùng công thức haversine -> usedFallback = true
        @Test
        void optimize_matrixFromFallback_marksUsedFallback() {
            when(hotspotService.getById(anyLong()))
                    .thenReturn(hotspot(1L, "Điểm A", 108.4380, 11.9404, null, null));
            when(goongDistanceService.getMatrix(anyList())).thenReturn(new DistanceMatrixResult(
                    new double[][]{{0, 1000}, {1000, 0}},
                    new double[][]{{0, 300}, {300, 0}},
                    true));

            OptimizedRouteResponse result = routeOptimizationService.optimize(
                    request(List.of(1L, 2L), OptimizeCriterion.TIME));

            assertTrue(result.getUsedFallback());
        }

        // UTCID11 - Normal: thời gian tham quan được cộng vào tổng thời gian ước tính
        @Test
        void optimize_withVisitDuration_addsToTotalEstimatedTime() {
            when(hotspotService.getById(1L))
                    .thenReturn(hotspot(1L, "Điểm A", 108.4380, 11.9404, 45L, null));
            when(hotspotService.getById(2L))
                    .thenReturn(hotspot(2L, "Điểm B", 108.4500, 11.9500, 30L, null));
            when(goongDistanceService.getMatrix(anyList())).thenReturn(new DistanceMatrixResult(
                    new double[][]{{0, 1000}, {1000, 0}},
                    new double[][]{{0, 600}, {600, 0}},
                    false));

            OptimizedRouteResponse result = routeOptimizationService.optimize(
                    request(List.of(1L, 2L), OptimizeCriterion.TIME));

            // 10 phút di chuyển + 45 phút + 30 phút tham quan = 85 phút
            assertEquals(85.0, result.getTotalEstimatedTime());
        }
    }
}
