package org.sep490.backend.config.payos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import vn.payos.PayOS;

/**
 * Tự động đăng ký (confirm) webhook URL với PayOS lúc khởi động.
 * <p>
 * Cần thiết vì PayOS chỉ gửi webhook khi thanh toán thành công tới URL đã được confirm. Khi test local
 * qua ngrok, URL đổi mỗi lần chạy lại ngrok nên việc confirm bằng tay dễ quên — component này confirm lại
 * URL trong {@code PAYOS_WEBHOOK_URL} mỗi lần app khởi động.
 * <p>
 * Bỏ qua nếu webhook URL rỗng hoặc vẫn là placeholder mặc định, và không để lỗi confirm làm chết app.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayOsWebhookRegistrar implements ApplicationRunner {

    private final PayOS payOS;
    private final PayOsProperties payOsProperties;

    @Override
    public void run(ApplicationArguments args) {
        String webhookUrl = payOsProperties.getWebhookUrl();

        if (webhookUrl == null || webhookUrl.isBlank() || webhookUrl.contains("your-ngrok")) {
            log.warn("[PayOS] Bỏ qua đăng ký webhook: PAYOS_WEBHOOK_URL chưa được cấu hình ({}).", webhookUrl);
            return;
        }

        try {
            payOS.webhooks().confirm(webhookUrl);
            log.info("[PayOS] Đã đăng ký webhook thành công: {}", webhookUrl);
        } catch (Exception e) {
            // Lỗi confirm không được làm chết app và không cần log (thường xảy ra khi ngrok chưa chạy)
        }
    }
}
