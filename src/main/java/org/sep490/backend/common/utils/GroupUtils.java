package org.sep490.backend.common.utils;


public class GroupUtils {


    public record TokenInfo(long groupId, long timestampSeconds) {
    }


    public static String generateToken(long groupId) {
        return ShareTokenUtils.generateToken(groupId);
    }


    public static TokenInfo parseToken(String token) {
        if (token == null || token.length() != 10) {
            throw new IllegalArgumentException("Token không hợp lệ. Độ dài bắt buộc là 10 ký tự.");
        }

        // Cắt chuỗi theo đúng cấu trúc: 4 đầu, 6 cuối
        String routePart = token.substring(0, 4);
        String timePart = token.substring(4, 10);

        long routeId = ShareTokenUtils.decodeFromBase62(routePart);
        long timestamp = ShareTokenUtils.decodeFromBase62(timePart);

        return new TokenInfo(routeId, timestamp);
    }
}
