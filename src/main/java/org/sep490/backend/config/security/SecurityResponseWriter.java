package org.sep490.backend.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.sep490.backend.common.exception.ApiErrorResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Ghi lỗi bảo mật ra response dưới dạng JSON.
 *
 * VÌ SAO CẦN LỚP NÀY: exception ném từ filter chain (chưa vào controller) KHÔNG đi qua
 * @RestControllerAdvice, nên GlobalExceptionHandler không bắt được. Ba nơi phải tự ghi
 * response: JwtDenylistFilter, RestAuthenticationEntryPoint, RestAccessDeniedHandler.
 * Gom về đây để cả ba không lệch format.
 *
 * DÙNG JACKSON, KHÔNG NỐI CHUỖI: bản nối chuỗi trước đây từng sinh ra JSON hỏng
 * ({ tatus":401 — mất dấu nháy). Ngoài ra message chứa dấu " hoặc \ sẽ phá vỡ JSON
 * nếu tự nối tay.
 *
 * DÙNG ĐÚNG ObjectMapper CỦA SPRING, KHÔNG TỰ new: mapper tự tạo không có cấu hình
 * của Spring Boot, nên LocalDateTime bị ghi thành mảng số [2026,8,6,14,19,36,...]
 * thay vì chuỗi ISO. Inject bean để dùng chung đúng con mapper mà @RestController và
 * GlobalExceptionHandler đang dùng — cấu hình Jackson đổi ở đâu thì response ở đây
 * tự đổi theo.
 */
@Component
@RequiredArgsConstructor
public class SecurityResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, int status,
                      String errorCode, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(status, errorCode, message));
    }
}
