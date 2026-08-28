package org.sep490.backend.common.utils;

import java.time.Instant;

public class ShareTokenUtils {

    private static final String BASE62_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = BASE62_ALPHABET.length();

    public record ShareTokenInfo(long id, long timestampSeconds) {
    }

    public static String generateToken(long id) {
        long currentTimestamp = Instant.now().getEpochSecond(); // Lấy thời gian hiện tại tính bằng Giây

        String routePart = encodeToBase62(id, 4);
        String timePart = encodeToBase62(currentTimestamp, 6);

        return routePart + timePart; // Ghép lại thành 10 ký tự
    }

    public static ShareTokenInfo parseToken(String token) {
        if (token == null || token.length() != 10) {
            throw new IllegalArgumentException("Token không hợp lệ. Độ dài bắt buộc là 10 ký tự.");
        }

        String idPart = token.substring(0, 4);
        String timePart = token.substring(4, 10);

        long id = ShareTokenUtils.decodeFromBase62(idPart);
        long timestamp = ShareTokenUtils.decodeFromBase62(timePart);

        return new ShareTokenInfo(id, timestamp);
    }

    public static String encodeToBase62(long value, int requiredLength) {
        StringBuilder sb = new StringBuilder();

        if (value == 0) {
            sb.append('0');
        } else {
            while (value > 0) {
                int remainder = (int) (value % BASE);
                sb.append(BASE62_ALPHABET.charAt(remainder));
                value /= BASE;
            }
        }

        sb.reverse();

        while (sb.length() < requiredLength) {
            sb.insert(0, '0');
        }

        if (sb.length() > requiredLength) {
            throw new IllegalArgumentException("Giá trị " + value + " quá lớn, không thể nhét vừa " + requiredLength + " ký tự Base62");
        }

        return sb.toString();
    }

    public static long decodeFromBase62(String base62String) {
        long result = 0;
        for (int i = 0; i < base62String.length(); i++) {
            char c = base62String.charAt(i);
            int value = BASE62_ALPHABET.indexOf(c);

            if (value == -1) {
                throw new IllegalArgumentException("Ký tự không hợp lệ trong chuỗi Base62: " + c);
            }

            result = result * BASE + value;
        }
        return result;
    }
}
