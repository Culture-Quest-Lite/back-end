package org.sep490.backend.common.utils;

import java.text.Normalizer;

public class TextUtils {

    private TextUtils() {}

    /**
     * Bỏ dấu tiếng Việt để so khớp tìm kiếm không dấu.
     * Kết quả phải khớp với hàm unaccent() của PostgreSQL, vì phía DB dùng unaccent()
     * còn phía Java dùng hàm này cho từ khóa người dùng nhập.
     */
    public static String removeDiacritics(String input) {
        if (input == null) {
            return null;
        }
        // NFD tách nguyên âm có dấu thành nguyên âm gốc + dấu tổ hợp, rồi xóa dấu
        String result = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        // đ/Đ là ký tự riêng chứ không phải chữ cái + dấu nên NFD không tách được,
        // trong khi unaccent() của Postgres map đ -> d. Phải xử lý tay cho khớp.
        return result.replace('đ', 'd').replace('Đ', 'D');
    }
}
