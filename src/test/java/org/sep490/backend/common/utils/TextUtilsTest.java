package org.sep490.backend.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kết quả phải khớp với hàm unaccent() của PostgreSQL, vì tìm kiếm không dấu
 * so khớp giữa từ khóa đã bỏ dấu bằng Java và cột đã bỏ dấu bằng unaccent().
 */
@DisplayName("TextUtils.removeDiacritics")
class TextUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "Ẩm thực,       Am thuc",
            "Lịch sử,       Lich su",
            "Thiên nhiên,   Thien nhien",
            "Nghệ thuật,    Nghe thuat",
            "Kiến trúc,     Kien truc",
            "Đà Nẵng,       Da Nang",
            "Đình làng,     Dinh lang",
            "đền chùa,      den chua",
            "Hưng Yên,      Hung Yen",
            "Bảo tàng,      Bao tang",
    })
    void removesVietnameseDiacritics(String input, String expected) {
        assertEquals(expected, TextUtils.removeDiacritics(input));
    }

    @Test
    @DisplayName("giữ nguyên chuỗi vốn đã không dấu")
    void keepsPlainTextUnchanged() {
        assertEquals("am thuc", TextUtils.removeDiacritics("am thuc"));
    }

    @Test
    @DisplayName("đ và Đ phải thành d và D — NFD không tự tách được")
    void mapsDStrokeLikePostgresUnaccent() {
        assertEquals("dDdD", TextUtils.removeDiacritics("đĐdD"));
    }

    @Test
    @DisplayName("null trả về null")
    void handlesNull() {
        assertNull(TextUtils.removeDiacritics(null));
    }
}
