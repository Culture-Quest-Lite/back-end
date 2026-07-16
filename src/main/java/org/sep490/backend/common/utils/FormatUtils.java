package org.sep490.backend.common.utils;

public class FormatUtils {

    private FormatUtils() {
    }

    /** Làm tròn 2 chữ số thập phân (dùng cho km). */
    public static Double round2(Double value) {
        if (value == null) return null;
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Đổi số phút sang chuỗi hiển thị:
     * dưới 60 phút -> "45 phút"; từ 60 phút -> "1 tiếng 5 phút", tròn giờ -> "2 tiếng".
     */
    public static String humanizeMinutes(Double minutes) {
        if (minutes == null) return null;

        long total = Math.round(minutes);
        if (total < 60) {
            return total + " phút";
        }

        long hours = total / 60;
        long remain = total % 60;
        return remain == 0 ? hours + " tiếng" : hours + " tiếng " + remain + " phút";
    }
}
