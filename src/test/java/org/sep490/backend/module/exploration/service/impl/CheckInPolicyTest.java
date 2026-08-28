package org.sep490.backend.module.exploration.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho CHÍNH SÁCH CHECK-IN (bán kính hợp lệ và dung sai GPS).
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
class CheckInPolicyTest {

    // =====================================================================
    // Function: toleranceFrom
    // =====================================================================
    @Nested
    @DisplayName("toleranceFrom")
    class ToleranceFromTest {

        // UTCID01 - Abnormal: thiết bị không gửi độ chính xác GPS (null) -> không cộng dung sai
        @Test
        void toleranceFrom_nullAccuracy_returnsZero() {
            assertEquals(0.0, CheckInPolicy.toleranceFrom(null));
        }

        // UTCID02 - Abnormal: độ chính xác âm (dữ liệu GPS lỗi) -> không cộng dung sai
        @Test
        void toleranceFrom_negativeAccuracy_returnsZero() {
            assertEquals(0.0, CheckInPolicy.toleranceFrom(-15.5));
        }

        // UTCID03 - Boundary: độ chính xác = 0 -> không cộng dung sai
        @Test
        void toleranceFrom_zeroAccuracy_returnsZero() {
            assertEquals(0.0, CheckInPolicy.toleranceFrom(0.0));
        }

        // UTCID04 - Normal: GPS tốt (25m) -> lấy đúng giá trị thiết bị báo
        @Test
        void toleranceFrom_accuracyBelowCap_returnsAccuracy() {
            assertEquals(25.0, CheckInPolicy.toleranceFrom(25.0));
        }

        // UTCID05 - Boundary: độ chính xác đúng bằng trần 100m -> giữ nguyên 100m
        @Test
        void toleranceFrom_accuracyEqualsCap_returnsCap() {
            assertEquals(100.0, CheckInPolicy.toleranceFrom(100.0));
        }

        // UTCID06 - Boundary: GPS quá tệ (350m) -> bị chặn ở trần 100m, tránh gian lận check-in
        @Test
        void toleranceFrom_accuracyAboveCap_isCappedAt100() {
            assertEquals(CheckInPolicy.MAX_GPS_ACCURACY_TOLERANCE_METERS,
                    CheckInPolicy.toleranceFrom(350.0));
        }
    }

    // =====================================================================
    // Function: effectiveRadius
    // =====================================================================
    @Nested
    @DisplayName("effectiveRadius")
    class EffectiveRadiusTest {

        // UTCID01 - Abnormal: hotspot chưa cấu hình bán kính -> dùng mặc định 50m
        @Test
        void effectiveRadius_nullRadius_returnsDefault50() {
            assertEquals(50, CheckInPolicy.effectiveRadius(null));
            assertEquals(CheckInPolicy.DEFAULT_RADIUS_METERS, CheckInPolicy.effectiveRadius(null));
        }

        // UTCID02 - Normal: hotspot cấu hình 200m -> dùng đúng 200m
        @Test
        void effectiveRadius_customRadius_returnsCustomValue() {
            assertEquals(200, CheckInPolicy.effectiveRadius(200));
        }

        // UTCID03 - Boundary: bán kính nhỏ nhất cho phép 20m
        @Test
        void effectiveRadius_minRadius_returnsMinValue() {
            assertEquals(20, CheckInPolicy.effectiveRadius(CheckInPolicy.MIN_RADIUS_METERS));
        }

        // UTCID04 - Boundary: bán kính lớn nhất cho phép 5000m
        @Test
        void effectiveRadius_maxRadius_returnsMaxValue() {
            assertEquals(5000, CheckInPolicy.effectiveRadius(CheckInPolicy.MAX_RADIUS_METERS));
        }
    }
}
