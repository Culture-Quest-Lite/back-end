package org.sep490.backend.module.exploration.service.impl;

public final class CheckInPolicy {

    // Ba hằng dưới đây chỉ là giá trị khởi tạo cho bảng check_in_radius_config
    // ở lần chạy đầu tiên. Sau đó admin toàn quyền chỉnh qua API cấu hình,
    // nên KHÔNG dùng chúng để validate request.
    public static final int DEFAULT_RADIUS_METERS = 50;

    public static final int MIN_RADIUS_METERS = 20;
    public static final int MAX_RADIUS_METERS = 5000;

    // Trần kỹ thuật cho chính giá trị admin nhập, chỉ để chặn gõ nhầm (vd. 5000000).
    public static final int ABSOLUTE_MAX_RADIUS_METERS = 100_000;

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
        return effectiveRadius(checkInRadius, DEFAULT_RADIUS_METERS);
    }

    /** {@code fallbackRadius} là bán kính mặc định admin đang đặt. */
    public static int effectiveRadius(Integer checkInRadius, Integer fallbackRadius) {
        if (checkInRadius != null) {
            return checkInRadius;
        }
        return fallbackRadius != null ? fallbackRadius : DEFAULT_RADIUS_METERS;
    }
}
