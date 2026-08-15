package org.sep490.backend.module.planner.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.config.goong.GoongClient;
import org.sep490.backend.config.goong.dto.GoongDistanceMatrixResponse;
import org.sep490.backend.config.redis.RedisCircuitBreaker;
import org.sep490.backend.module.planner.dto.record.DistanceMatrixResult;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho MA TRẬN KHOẢNG CÁCH GOONG (có cache Redis và fallback Haversine khi API lỗi).
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoongDistanceServiceImplTest {

    @Mock private GoongClient goongClient;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private RedisCircuitBreaker circuitBreaker;

    @InjectMocks private GoongDistanceServiceImpl goongDistanceService;

    /**
     * Văn Miếu và Hồ Gươm (Hà Nội) — cách nhau khoảng 1.5km đường chim bay.
     * Thứ tự mỗi phần tử là {vĩ độ, kinh độ}, đúng như RouteOptimizationServiceImpl truyền vào.
     */
    private static final List<double[]> TWO_POINTS = List.of(
            new double[]{21.0278, 105.8355},
            new double[]{21.0287, 105.8523});

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(circuitBreaker.read(anyString(), any(), any())).thenAnswer(inv -> {
            Supplier<?> supplier = inv.getArgument(1);
            return supplier.get();
        });
        doAnswer(inv -> {
            Runnable runnable = inv.getArgument(1);
            runnable.run();
            return null;
        }).when(circuitBreaker).write(anyString(), any());
    }

    private static GoongDistanceMatrixResponse.ValueText value(long v) {
        GoongDistanceMatrixResponse.ValueText valueText = new GoongDistanceMatrixResponse.ValueText();
        valueText.setValue(v);
        return valueText;
    }

    /** Ma trận 2x2 hợp lệ: 0 trên đường chéo, 1800m/420s giữa hai điểm. */
    private static GoongDistanceMatrixResponse validMatrix() {
        GoongDistanceMatrixResponse response = new GoongDistanceMatrixResponse();
        List<GoongDistanceMatrixResponse.Row> rows = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            GoongDistanceMatrixResponse.Row row = new GoongDistanceMatrixResponse.Row();
            List<GoongDistanceMatrixResponse.Element> elements = new ArrayList<>();
            for (int j = 0; j < 2; j++) {
                GoongDistanceMatrixResponse.Element element = new GoongDistanceMatrixResponse.Element();
                element.setDistance(value(i == j ? 0 : 1800));
                element.setDuration(value(i == j ? 0 : 420));
                elements.add(element);
            }
            row.setElements(elements);
            rows.add(row);
        }
        response.setRows(rows);
        return response;
    }

    // =====================================================================
    // Function: getMatrix
    // =====================================================================
    @Nested
    @DisplayName("getMatrix")
    class GetMatrixTest {

        // UTCID01 - Normal: cache hit -> trả ngay từ Redis, KHÔNG gọi API Goong (tốn phí)
        @Test
        void getMatrix_cacheHit_returnsCachedWithoutCallingApi() {
            DistanceMatrixResult cached = new DistanceMatrixResult(
                    new double[][]{{0, 1500}, {1500, 0}},
                    new double[][]{{0, 360}, {360, 0}}, false);
            when(valueOps.get(anyString())).thenReturn(cached);

            DistanceMatrixResult result = goongDistanceService.getMatrix(TWO_POINTS);

            assertSame(cached, result);
            verifyNoInteractions(goongClient);
        }

        // UTCID02 - Normal: cache miss -> gọi Goong, trả đúng số liệu API và ghi cache
        @Test
        void getMatrix_cacheMiss_callsGoongAndCachesResult() {
            when(valueOps.get(anyString())).thenReturn(null);
            when(goongClient.distanceMatrix(TWO_POINTS, TWO_POINTS, "bike")).thenReturn(validMatrix());

            DistanceMatrixResult result = goongDistanceService.getMatrix(TWO_POINTS);

            assertFalse(result.fromFallback());
            assertEquals(1800.0, result.distanceMeters()[0][1]);
            assertEquals(420.0, result.durationSeconds()[0][1]);
            assertEquals(0.0, result.distanceMeters()[0][0]);
            verify(valueOps).set(anyString(), eq(result), any());
        }

        // UTCID03 - Abnormal: Goong ném lỗi -> fallback Haversine, KHÔNG ghi cache kết quả ước lượng
        @Test
        void getMatrix_goongThrows_fallsBackToHaversineWithoutCaching() {
            when(valueOps.get(anyString())).thenReturn(null);
            when(goongClient.distanceMatrix(anyList(), anyList(), anyString()))
                    .thenThrow(new RuntimeException("Goong API 429 Too Many Requests"));

            DistanceMatrixResult result = goongDistanceService.getMatrix(TWO_POINTS);

            assertTrue(result.fromFallback());
            assertTrue(result.distanceMeters()[0][1] > 0);
            verify(valueOps, never()).set(anyString(), any(), any());
        }

        // UTCID04 - Abnormal: Goong trả null -> fallback Haversine
        @Test
        void getMatrix_goongReturnsNull_fallsBackToHaversine() {
            when(valueOps.get(anyString())).thenReturn(null);
            when(goongClient.distanceMatrix(anyList(), anyList(), anyString())).thenReturn(null);

            assertTrue(goongDistanceService.getMatrix(TWO_POINTS).fromFallback());
        }

        // UTCID05 - Abnormal: số dòng trả về không khớp số điểm -> dữ liệu hỏng, fallback
        @Test
        void getMatrix_rowCountMismatch_fallsBackToHaversine() {
            when(valueOps.get(anyString())).thenReturn(null);
            GoongDistanceMatrixResponse broken = validMatrix();
            broken.setRows(List.of(broken.getRows().get(0))); // chỉ còn 1 dòng cho 2 điểm
            when(goongClient.distanceMatrix(anyList(), anyList(), anyString())).thenReturn(broken);

            assertTrue(goongDistanceService.getMatrix(TWO_POINTS).fromFallback());
        }

        // UTCID06 - Abnormal: một phần tử thiếu khoảng cách -> fallback toàn bộ ma trận
        @Test
        void getMatrix_elementMissingDistance_fallsBackToHaversine() {
            when(valueOps.get(anyString())).thenReturn(null);
            GoongDistanceMatrixResponse broken = validMatrix();
            broken.getRows().get(0).getElements().get(1).setDistance(null);
            when(goongClient.distanceMatrix(anyList(), anyList(), anyString())).thenReturn(broken);

            assertTrue(goongDistanceService.getMatrix(TWO_POINTS).fromFallback());
        }

        // UTCID07 - Boundary: fallback ước lượng thời gian theo vận tốc 25km/h
        @Test
        void getMatrix_fallback_estimatesDurationAt25Kmh() {
            when(valueOps.get(anyString())).thenReturn(null);
            when(goongClient.distanceMatrix(anyList(), anyList(), anyString())).thenReturn(null);

            DistanceMatrixResult result = goongDistanceService.getMatrix(TWO_POINTS);

            double expectedSeconds = result.distanceMeters()[0][1] / (25_000.0 / 3600.0);
            assertEquals(expectedSeconds, result.durationSeconds()[0][1], 0.001);
            assertEquals(0.0, result.distanceMeters()[0][0]);
        }

        // UTCID08 - Boundary: cùng bộ điểm gọi 2 lần -> dùng chung 1 khóa cache
        @Test
        void getMatrix_samePoints_useSameCacheKey() {
            when(valueOps.get(anyString())).thenReturn(null);
            when(goongClient.distanceMatrix(anyList(), anyList(), anyString())).thenReturn(validMatrix());

            goongDistanceService.getMatrix(TWO_POINTS);
            goongDistanceService.getMatrix(List.of(
                    new double[]{21.0278, 105.8355},
                    new double[]{21.0287, 105.8523}));

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(valueOps, times(2)).get(captor.capture());
            assertEquals(captor.getAllValues().get(0), captor.getAllValues().get(1));
            assertTrue(captor.getAllValues().get(0).startsWith("goong:matrix:"));
        }

        // UTCID09 - Boundary: đổi thứ tự điểm -> khóa cache phải khác nhau
        @Test
        void getMatrix_differentPointOrder_usesDifferentCacheKey() {
            when(valueOps.get(anyString())).thenReturn(null);
            when(goongClient.distanceMatrix(anyList(), anyList(), anyString())).thenReturn(validMatrix());

            goongDistanceService.getMatrix(TWO_POINTS);
            goongDistanceService.getMatrix(List.of(TWO_POINTS.get(1), TWO_POINTS.get(0)));

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(valueOps, times(2)).get(captor.capture());
            assertNotEquals(captor.getAllValues().get(0), captor.getAllValues().get(1));
        }

        // UTCID10 - Abnormal: Redis chết -> vẫn gọi Goong và trả kết quả, không ném lỗi
        @Test
        void getMatrix_redisDown_stillReturnsResultFromApi() {
            doReturn(null).when(circuitBreaker).read(anyString(), any(), any());
            when(goongClient.distanceMatrix(anyList(), anyList(), anyString())).thenReturn(validMatrix());

            DistanceMatrixResult result = goongDistanceService.getMatrix(TWO_POINTS);

            assertFalse(result.fromFallback());
            assertEquals(1800.0, result.distanceMeters()[0][1]);
        }

        // UTCID11 - Abnormal: points = null -> chặn trước khi chạm Redis
        @Test
        void getMatrix_nullPoints_throwsEmptyCoordinates() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> goongDistanceService.getMatrix(null));

            assertEquals("Danh sách toạ độ không được để trống", ex.getMessage());
            verifyNoInteractions(goongClient);
        }

        // UTCID12 - Abnormal: points = [] -> chặn trước khi chạm Redis
        @Test
        void getMatrix_emptyPoints_throwsEmptyCoordinates() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> goongDistanceService.getMatrix(List.of()));

            assertEquals("Danh sách toạ độ không được để trống", ex.getMessage());
            verifyNoInteractions(goongClient);
        }

        // UTCID13 - Boundary: chỉ 1 điểm {21.0278, 105.8355} -> chưa đủ để tính ma trận
        @Test
        void getMatrix_singlePoint_throwsNotEnoughPoints() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> goongDistanceService.getMatrix(
                            List.of(new double[]{21.0278, 105.8355})));

            assertEquals("Cần ít nhất 2 điểm để tính ma trận khoảng cách", ex.getMessage());
            verifyNoInteractions(goongClient);
        }

        // UTCID14 - Boundary: 26 điểm -> vượt trần 25 điểm của Goong
        @Test
        void getMatrix_tooManyPoints_throwsLimitExceeded() {
            List<double[]> points = new ArrayList<>();
            for (int i = 0; i < 26; i++) {
                points.add(new double[]{21.0278 + i * 0.001, 105.8355});
            }

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> goongDistanceService.getMatrix(points));

            assertEquals("Không được vượt quá 25 điểm cho một lần tính khoảng cách", ex.getMessage());
            verifyNoInteractions(goongClient);
        }

        // UTCID15 - Abnormal: một điểm chỉ có 1 giá trị {21.0278} -> sai định dạng
        @Test
        void getMatrix_malformedPoint_throwsInvalidPointFormat() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> goongDistanceService.getMatrix(List.of(
                            new double[]{21.0278, 105.8355},
                            new double[]{21.0287})));

            assertEquals("Mỗi điểm phải gồm đúng 2 giá trị vĩ độ và kinh độ", ex.getMessage());
            verifyNoInteractions(goongClient);
        }

        // UTCID16 - Boundary: vĩ độ 91.0 -> vượt biên [-90, 90]
        @Test
        void getMatrix_latitudeOutOfRange_throwsInvalidLatitude() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> goongDistanceService.getMatrix(List.of(
                            new double[]{21.0278, 105.8355},
                            new double[]{91.0, 105.8523})));

            assertEquals("Vĩ độ phải nằm trong khoảng -90 đến 90", ex.getMessage());
            verifyNoInteractions(goongClient);
        }

        // UTCID17 - Boundary: kinh độ 180.5 -> vượt biên [-180, 180]
        @Test
        void getMatrix_longitudeOutOfRange_throwsInvalidLongitude() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> goongDistanceService.getMatrix(List.of(
                            new double[]{21.0278, 105.8355},
                            new double[]{21.0287, 180.5})));

            assertEquals("Kinh độ phải nằm trong khoảng -180 đến 180", ex.getMessage());
            verifyNoInteractions(goongClient);
        }
    }
}
