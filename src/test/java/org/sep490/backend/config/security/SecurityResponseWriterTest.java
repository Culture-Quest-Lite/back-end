package org.sep490.backend.config.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REGRESSION TEST cho hai lỗi JSON đã gặp thật trên Swagger.
 *
 * LỖI 1 — JSON không parse được. Bản đầu nối chuỗi tay và viết sai escape:
 *     "{\status\":%d, ..."     ->  sinh ra   { tatus":401, ...
 * Java vẫn compile vì \s là escape hợp lệ (khoảng trắng, Java 15+), nên chỉ lộ ra
 * khi client cố parse và báo "can't parse JSON".
 *
 * LỖI 2 — timestamp ra mảng số [2026,8,6,14,19,36,149150400] thay vì chuỗi ISO,
 * do tự new ObjectMapper() nên thiếu cấu hình của Spring Boot. Test cũ chỉ kiểm tra
 * has("timestamp") nên KHÔNG bắt được — bài học: phải khẳng định cả ĐỊNH DẠNG,
 * không chỉ sự tồn tại của trường.
 *
 * Test PARSE JSON THẬT thay vì so chuỗi — cách duy nhất bắt được lỗi loại này.
 */
@DisplayName("SecurityResponseWriter — response phải là JSON hợp lệ")
class SecurityResponseWriterTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * Lấy ĐÚNG bean ObjectMapper mà Spring Boot tạo ra, thay vì tự new hay tự gọi
     * Jackson2ObjectMapperBuilder — cả hai cách đó đều thiếu phần cấu hình do
     * auto-configuration áp vào (cụ thể là tắt WRITE_DATES_AS_TIMESTAMPS), nên test
     * sẽ mô phỏng sai môi trường thật và lỗi timestamp lại lọt lần nữa.
     *
     * ApplicationContextRunner nạp riêng JacksonAutoConfiguration, nhẹ hơn nhiều so với
     * @SpringBootTest mà vẫn giữ được đúng hành vi của mapper thật.
     */
    private SecurityResponseWriter writer() {
        SecurityResponseWriter[] holder = new SecurityResponseWriter[1];
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .run(context -> holder[0] =
                        new SecurityResponseWriter(context.getBean(ObjectMapper.class)));
        return holder[0];
    }

    @Test
    @DisplayName("401 sinh ra JSON parse được, đủ 3 trường")
    void ghi401RaJsonHopLe() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer().write(response, 401, "UNAUTHORIZED",
                "Bạn cần đăng nhập để thực hiện thao tác này");

        JsonNode json = mapper.readTree(response.getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(401);
        assertThat(json.get("errorCode").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(json.get("message").asText())
                .isEqualTo("Bạn cần đăng nhập để thực hiện thao tác này");
    }

    @Test
    @DisplayName("403 sinh ra JSON parse được")
    void ghi403RaJsonHopLe() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer().write(response, 403, "FORBIDDEN",
                "Bạn không có quyền thực hiện thao tác này");

        JsonNode json = mapper.readTree(response.getContentAsString());
        assertThat(json.get("status").asInt()).isEqualTo(403);
        assertThat(json.get("errorCode").asText()).isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("Header status và content-type được set đúng")
    void setDungHeader() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer().write(response, 401, "TOKEN_REVOKED", "Phiên đăng nhập đã kết thúc");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
    }

    @Test
    @DisplayName("Tiếng Việt có dấu không bị vỡ mã")
    void tiengVietKhongViMa() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer().write(response, 401, "TOKEN_REVOKED",
                "Phiên đăng nhập đã kết thúc. Vui lòng đăng nhập lại");

        JsonNode json = mapper.readTree(response.getContentAsString());
        assertThat(json.get("message").asText())
                .isEqualTo("Phiên đăng nhập đã kết thúc. Vui lòng đăng nhập lại");
    }

    /**
     * Lý do bỏ hẳn cách nối chuỗi: message chứa dấu nháy kép sẽ phá vỡ JSON.
     * Hiện cả 3 nơi gọi đều dùng chuỗi hằng an toàn, nhưng người sau có thể
     * truyền message động (ví dụ kèm tên tài nguyên do người dùng nhập).
     */
    @Test
    @DisplayName("Message chứa dấu \" và \\ vẫn sinh JSON hợp lệ")
    void messageCoKyTuDacBietVanHopLe() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String hiemHoc = "Tài nguyên \"abc\" không tồn tại \\ đường dẫn sai";

        writer().write(response, 403, "FORBIDDEN", hiemHoc);

        JsonNode json = mapper.readTree(response.getContentAsString());
        assertThat(json.get("message").asText()).isEqualTo(hiemHoc);
    }

    /**
     * BẮT LỖI 2. Trước khi sửa, timestamp là mảng [2026,8,6,...] nên isTextual() = false
     * và frontend không dựng được Date. Đây là test mà lẽ ra tôi phải viết ngay lần đầu.
     */
    @Test
    @DisplayName("timestamp là chuỗi ISO-8601, KHÔNG phải mảng số")
    void timestampLaChuoiIso() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer().write(response, 401, "UNAUTHORIZED", "test");

        JsonNode timestamp = mapper.readTree(response.getContentAsString()).get("timestamp");
        assertThat(timestamp).isNotNull();
        assertThat(timestamp.isArray())
                .as("timestamp ra mảng số nghĩa là ObjectMapper thiếu cấu hình của Spring Boot")
                .isFalse();
        assertThat(timestamp.isTextual()).isTrue();

        // Parse lại được thành LocalDateTime -> frontend cũng dựng Date được
        assertThat(LocalDateTime.parse(timestamp.asText())).isNotNull();
    }
}
