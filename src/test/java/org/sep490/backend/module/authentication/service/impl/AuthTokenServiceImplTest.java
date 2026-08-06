package org.sep490.backend.module.authentication.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.config.redis.RedisCircuitBreaker;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho kho TOKEN XÁC THỰC trên Redis (OTP, reset mật khẩu, denylist).
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthTokenServiceImplTest {

    @Mock private StringRedisTemplate authRedis;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private RedisCircuitBreaker circuitBreaker;

    @InjectMocks private AuthTokenServiceImpl authTokenService;

    private static final String EMAIL = "a@gmail.com";

    @BeforeEach
    void setUp() {
        when(authRedis.opsForValue()).thenReturn(valueOperations);

        // circuitBreaker.read(...) chạy thẳng supplier
        when(circuitBreaker.read(anyString(), any(), any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        });

        // circuitBreaker.write(...) chạy thẳng runnable
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(circuitBreaker).write(anyString(), any());
    }

    // =====================================================================
    // Function: acquireResendSlot
    // =====================================================================
    @Nested
    @DisplayName("acquireResendSlot")
    class AcquireResendSlotTest {

        // UTCID01 - Normal: chưa từng gửi OTP -> chiếm được slot, không phải chờ
        @Test
        void acquireResendSlot_slotFree_returnsZero() {
            when(valueOperations.setIfAbsent(eq("otp:cooldown:a@gmail.com"), eq("1"), any(Duration.class)))
                    .thenReturn(true);

            assertEquals(0L, authTokenService.acquireResendSlot(EMAIL));
            verify(authRedis, never()).getExpire(anyString(), any(TimeUnit.class));
        }

        // UTCID02 - Abnormal: đang trong cooldown, còn 18 giây -> trả về số giây còn lại
        @Test
        void acquireResendSlot_inCooldown_returnsRemainingTtl() {
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(false);
            when(authRedis.getExpire("otp:cooldown:a@gmail.com", TimeUnit.SECONDS)).thenReturn(18L);

            assertEquals(18L, authTokenService.acquireResendSlot(EMAIL));
        }

        // UTCID03 - Boundary: còn đúng 1 giây cuối
        @Test
        void acquireResendSlot_oneSecondLeft_returnsOne() {
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(false);
            when(authRedis.getExpire(anyString(), any(TimeUnit.class))).thenReturn(1L);

            assertEquals(1L, authTokenService.acquireResendSlot(EMAIL));
        }

        // UTCID04 - Boundary: TTL trả 0 (key vừa hết hạn) -> trả cooldown mặc định 30s
        @Test
        void acquireResendSlot_ttlZero_returnsDefaultCooldown() {
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(false);
            when(authRedis.getExpire(anyString(), any(TimeUnit.class))).thenReturn(0L);

            assertEquals(30L, authTokenService.acquireResendSlot(EMAIL));
        }

        // UTCID05 - Abnormal: Redis trả TTL = null -> trả cooldown mặc định 30s
        @Test
        void acquireResendSlot_ttlNull_returnsDefaultCooldown() {
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(false);
            when(authRedis.getExpire(anyString(), any(TimeUnit.class))).thenReturn(null);

            assertEquals(30L, authTokenService.acquireResendSlot(EMAIL));
        }

        // UTCID06 - Boundary: email có hoa/thường và khoảng trắng -> chuẩn hóa về cùng 1 key
        @Test
        void acquireResendSlot_emailWithSpacesAndCase_normalizesKey() {
            when(valueOperations.setIfAbsent(eq("otp:cooldown:a@gmail.com"), anyString(), any(Duration.class)))
                    .thenReturn(true);

            assertEquals(0L, authTokenService.acquireResendSlot("  A@Gmail.COM  "));
        }
    }

    // =====================================================================
    // Function: isAttemptExceeded
    // =====================================================================
    @Nested
    @DisplayName("isAttemptExceeded")
    class IsAttemptExceededTest {

        // UTCID01 - Normal: lần nhập đầu tiên -> đặt cửa sổ đếm 15 phút, chưa vượt
        @Test
        void isAttemptExceeded_firstAttempt_setsWindowAndReturnsFalse() {
            when(valueOperations.increment("otp:attempt:a@gmail.com")).thenReturn(1L);

            assertFalse(authTokenService.isAttemptExceeded(EMAIL));
            verify(authRedis).expire("otp:attempt:a@gmail.com", Duration.ofMinutes(15));
        }

        // UTCID02 - Normal: lần thứ 2 -> KHÔNG đặt lại cửa sổ đếm (chống gia hạn vô hạn)
        @Test
        void isAttemptExceeded_secondAttempt_doesNotResetWindow() {
            when(valueOperations.increment(anyString())).thenReturn(2L);

            assertFalse(authTokenService.isAttemptExceeded(EMAIL));
            verify(authRedis, never()).expire(anyString(), any(Duration.class));
        }

        // UTCID03 - Boundary: đúng lần thứ 5 = ngưỡng tối đa -> vẫn cho phép
        @Test
        void isAttemptExceeded_exactlyMaxAttempts_returnsFalse() {
            when(valueOperations.increment(anyString())).thenReturn(5L);

            assertFalse(authTokenService.isAttemptExceeded(EMAIL));
        }

        // UTCID04 - Boundary: lần thứ 6 -> vượt ngưỡng, chặn
        @Test
        void isAttemptExceeded_oneOverMaxAttempts_returnsTrue() {
            when(valueOperations.increment(anyString())).thenReturn(6L);

            assertTrue(authTokenService.isAttemptExceeded(EMAIL));
        }

        // UTCID05 - Abnormal: Redis trả null -> fail-open, không chặn người dùng thật
        @Test
        void isAttemptExceeded_redisReturnsNull_returnsFalse() {
            when(valueOperations.increment(anyString())).thenReturn(null);

            assertFalse(authTokenService.isAttemptExceeded(EMAIL));
            verify(authRedis, never()).expire(anyString(), any(Duration.class));
        }
    }

    // =====================================================================
    // Function: findUserIdByResetToken
    // =====================================================================
    @Nested
    @DisplayName("findUserIdByResetToken")
    class FindUserIdByResetTokenTest {

        private static final String TOKEN = "reset-token-abc";

        // UTCID01 - Normal: token hợp lệ -> trả userId
        @Test
        void findUserIdByResetToken_validToken_returnsUserId() {
            when(valueOperations.get("pwreset:reset-token-abc")).thenReturn("42");

            assertEquals(Optional.of(42L), authTokenService.findUserIdByResetToken(TOKEN));
        }

        // UTCID02 - Abnormal: token không tồn tại hoặc đã hết TTL -> rỗng
        @Test
        void findUserIdByResetToken_tokenNotFound_returnsEmpty() {
            when(valueOperations.get(anyString())).thenReturn(null);

            assertTrue(authTokenService.findUserIdByResetToken(TOKEN).isEmpty());
        }

        // UTCID03 - Abnormal: giá trị trong Redis không phải số -> rỗng, không ném lỗi
        @Test
        void findUserIdByResetToken_nonNumericValue_returnsEmptyWithoutThrowing() {
            when(valueOperations.get(anyString())).thenReturn("khong-phai-so");

            assertTrue(authTokenService.findUserIdByResetToken(TOKEN).isEmpty());
        }
    }

    // =====================================================================
    // Function: denyToken
    // =====================================================================
    @Nested
    @DisplayName("denyToken")
    class DenyTokenTest {

        // UTCID01 - Abnormal: jti = null -> bỏ qua, không ghi Redis
        @Test
        void denyToken_nullJti_doesNothing() {
            authTokenService.denyToken(null, Duration.ofMinutes(5));

            verify(circuitBreaker, never()).write(anyString(), any());
        }

        // UTCID02 - Abnormal: thời gian sống còn lại = null -> bỏ qua
        @Test
        void denyToken_nullRemainingLifetime_doesNothing() {
            authTokenService.denyToken("jti-001", null);

            verify(circuitBreaker, never()).write(anyString(), any());
        }

        // UTCID03 - Boundary: token đã hết hạn (âm) -> không cần đưa vào denylist
        @Test
        void denyToken_negativeLifetime_doesNothing() {
            authTokenService.denyToken("jti-001", Duration.ofSeconds(-1));

            verify(circuitBreaker, never()).write(anyString(), any());
        }

        // UTCID04 - Boundary: thời gian còn lại đúng bằng 0 -> không ghi
        @Test
        void denyToken_zeroLifetime_doesNothing() {
            authTokenService.denyToken("jti-001", Duration.ZERO);

            verify(circuitBreaker, never()).write(anyString(), any());
        }

        // UTCID05 - Normal: token còn 5 phút -> ghi vào denylist đúng TTL còn lại
        @Test
        void denyToken_validToken_writesToDenylistWithRemainingTtl() {
            authTokenService.denyToken("jti-001", Duration.ofMinutes(5));

            verify(valueOperations).set("denylist:jti:jti-001", "1", Duration.ofMinutes(5));
        }
    }

    // =====================================================================
    // Function: isTokenDenied
    // =====================================================================
    @Nested
    @DisplayName("isTokenDenied")
    class IsTokenDeniedTest {

        // UTCID01 - Abnormal: jti = null -> không chặn, không gọi Redis
        @Test
        void isTokenDenied_nullJti_returnsFalseWithoutRedisCall() {
            assertFalse(authTokenService.isTokenDenied(null));

            verify(circuitBreaker, never()).read(anyString(), any(), any());
        }

        // UTCID02 - Abnormal: token đã bị thu hồi (đăng xuất) -> chặn
        @Test
        void isTokenDenied_tokenInDenylist_returnsTrue() {
            when(authRedis.hasKey("denylist:jti:jti-001")).thenReturn(true);

            assertTrue(authTokenService.isTokenDenied("jti-001"));
        }

        // UTCID03 - Normal: token chưa bị thu hồi -> cho qua
        @Test
        void isTokenDenied_tokenNotInDenylist_returnsFalse() {
            when(authRedis.hasKey(anyString())).thenReturn(false);

            assertFalse(authTokenService.isTokenDenied("jti-001"));
        }

        // UTCID04 - Abnormal: Redis trả null -> fail-open, cho qua
        @Test
        void isTokenDenied_redisReturnsNull_returnsFalse() {
            when(authRedis.hasKey(anyString())).thenReturn(null);

            assertFalse(authTokenService.isTokenDenied("jti-001"));
        }
    }

    // =====================================================================
    // Function: savePasswordResetToken
    // =====================================================================
    @Nested
    @DisplayName("savePasswordResetToken")
    class SavePasswordResetTokenTest {

        // UTCID01 - Normal: lần đầu yêu cầu -> lưu 2 chiều token<->userId, TTL 15 phút
        @Test
        void savePasswordResetToken_firstRequest_savesBothDirections() {
            when(valueOperations.get("pwreset:user:42")).thenReturn(null);

            authTokenService.savePasswordResetToken("token-moi", 42L);

            verify(valueOperations).set("pwreset:token-moi", "42", Duration.ofMinutes(15));
            verify(valueOperations).set("pwreset:user:42", "token-moi", Duration.ofMinutes(15));
            verify(authRedis, never()).delete(anyString());
        }

        // UTCID02 - Normal: đã có token cũ -> xóa token cũ trước khi lưu token mới
        @Test
        void savePasswordResetToken_previousTokenExists_deletesOldToken() {
            when(valueOperations.get("pwreset:user:42")).thenReturn("token-cu");

            authTokenService.savePasswordResetToken("token-moi", 42L);

            verify(authRedis).delete("pwreset:token-cu");
            verify(valueOperations).set("pwreset:token-moi", "42", Duration.ofMinutes(15));
        }

        // UTCID03 - Boundary: token cũ trùng token mới -> vẫn ghi đè bình thường
        @Test
        void savePasswordResetToken_sameTokenAgain_overwritesSafely() {
            when(valueOperations.get("pwreset:user:42")).thenReturn("token-moi");

            authTokenService.savePasswordResetToken("token-moi", 42L);

            verify(authRedis).delete("pwreset:token-moi");
            verify(valueOperations).set("pwreset:token-moi", "42", Duration.ofMinutes(15));
        }
    }

    // =====================================================================
    // Function: findOtp / saveOtp / deleteOtp
    // =====================================================================
    @Nested
    @DisplayName("findOtp")
    class FindOtpTest {

        // UTCID01 - Normal: OTP còn hạn -> trả về mã
        @Test
        void findOtp_otpExists_returnsCode() {
            when(valueOperations.get("otp:a@gmail.com")).thenReturn("749271");

            assertEquals(Optional.of("749271"), authTokenService.findOtp(EMAIL));
        }

        // UTCID02 - Abnormal: OTP đã hết TTL (Redis tự xóa) -> rỗng
        @Test
        void findOtp_otpExpired_returnsEmpty() {
            when(valueOperations.get(anyString())).thenReturn(null);

            assertTrue(authTokenService.findOtp(EMAIL).isEmpty());
        }

        // UTCID03 - Normal: lưu OTP với TTL 2 phút
        @Test
        void saveOtp_valid_savesWithTwoMinuteTtl() {
            authTokenService.saveOtp(EMAIL, "749271");

            verify(valueOperations).set("otp:a@gmail.com", "749271", Duration.ofMinutes(2));
        }

        // UTCID04 - Normal: xóa OTP -> xóa cả key đếm số lần nhập sai
        @Test
        void deleteOtp_valid_deletesOtpAndAttemptKeys() {
            authTokenService.deleteOtp(EMAIL);

            verify(authRedis).delete("otp:a@gmail.com");
            verify(authRedis).delete("otp:attempt:a@gmail.com");
        }
    }
}
