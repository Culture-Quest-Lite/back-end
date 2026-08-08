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
 * Bỏ qua nếu webhook URL rỗng, vẫn là placeholder mặc định, hoặc không phải URL public (localhost/http),
 * và không để lỗi confirm làm chết app. Khi chạy local không có tunnel, set {@code PAYOS_AUTO_REGISTER_WEBHOOK=false}
 * để tắt hẳn bước này.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayOsWebhookRegistrar implements ApplicationRunner {

    private final PayOS payOS;
    private final PayOsProperties payOsProperties;

    private static final String SKIP_HINT = "Thanh toán vẫn chạy được, nhưng hóa đơn sẽ ở trạng thái PENDING "
            + "cho tới khi client gọi API đối soát.";

    @Override
    public void run(ApplicationArguments args) {
        if (!payOsProperties.isAutoRegisterWebhook()) {
            log.info("[PayOS] Bỏ qua đăng ký webhook (payos.auto-register-webhook=false). {}", SKIP_HINT);
            return;
        }

        String webhookUrl = payOsProperties.getWebhookUrl();

        if (!isPublicHttpsUrl(webhookUrl)) {
            log.warn("[PayOS] Bỏ qua đăng ký webhook: PAYOS_WEBHOOK_URL chưa phải URL https public ({}). {}",
                    webhookUrl, SKIP_HINT);
            return;
        }

        try {
            payOS.webhooks().confirm(webhookUrl);
            log.info("[PayOS] Đã đăng ký webhook thành công: {}", webhookUrl);
        } catch (Exception e) {
            log.warn("[PayOS] Không đăng ký được webhook {} (thường do tunnel ngrok chưa chạy hoặc URL đã đổi): {}. "
                            + "{} Set PAYOS_AUTO_REGISTER_WEBHOOK=false nếu chạy local không cần webhook.",
                    webhookUrl, e.getMessage(), SKIP_HINT);
            log.debug("[PayOS] Chi tiết lỗi confirm webhook", e);
        }
    }

    /** PayOS chỉ nhận webhook qua https public; localhost/http chắc chắn fail nên không cần gọi API. */
    private boolean isPublicHttpsUrl(String url) {
        if (url == null || url.isBlank() || url.contains("your-ngrok")) {
            return false;
        }
        return url.startsWith("https://")
                && !url.contains("localhost")
                && !url.contains("127.0.0.1");
    }
}
