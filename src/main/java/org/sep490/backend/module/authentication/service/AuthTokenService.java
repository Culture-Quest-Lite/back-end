package org.sep490.backend.module.authentication.service;

import java.time.Duration;
import java.util.Optional;

/**
 * Lưu OTP, token đặt lại mật khẩu, bộ đếm chống lạm dụng và denylist JWT trên Redis.
 *
 * ĐÁNH ĐỔI CÓ CHỦ Ý: đây là ngoại lệ với nguyên tắc "Redis không bao giờ là nguồn sự thật".
 * Redis restart sẽ mất OTP đang bay — người dùng chỉ cần bấm gửi lại. Đổi lại xoá được
 * hai bảng phình vô hạn (email_otps, password_reset_tokens) và toàn bộ logic hết hạn thủ công.
 */
public interface AuthTokenService {

    // ---------- OTP ----------

    void saveOtp(String email, String otpCode);

    Optional<String> findOtp(String email);

    /** OTP dùng một lần: xoá cả mã lẫn bộ đếm số lần sai. */
    void deleteOtp(String email);

    /**
     * Cooldown gửi lại OTP bằng SET NX EX (atomic).
     *
     * @return số giây còn phải đợi; 0 nghĩa là được phép gửi
     */
    long acquireResendSlot(String email);

    /**
     * Đếm số lần nhập OTP sai để chặn vét cạn.
     *
     * @return true nếu đã vượt ngưỡng và phải chặn
     */
    boolean isAttemptExceeded(String email);

    int maxOtpAttempts();

    // ---------- Token đặt lại mật khẩu ----------

    /** Phát hành token mới, đồng thời vô hiệu token cũ của user (nếu có). */
    void savePasswordResetToken(String token, Long userId);

    Optional<Long> findUserIdByResetToken(String token);

    void deletePasswordResetToken(String token, Long userId);

    // ---------- Denylist JWT ----------

    /** Chặn access token cho tới khi nó hết hạn tự nhiên. */
    void denyToken(String jti, Duration remainingLifetime);

    boolean isTokenDenied(String jti);
}
