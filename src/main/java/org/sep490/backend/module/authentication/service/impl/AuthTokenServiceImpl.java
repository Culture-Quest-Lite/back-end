package org.sep490.backend.module.authentication.service.impl;

import org.sep490.backend.module.authentication.service.AuthTokenService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.config.redis.CacheNames;
import org.sep490.backend.config.redis.RedisCircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthTokenServiceImpl implements AuthTokenService {

    private static final Duration OTP_TTL = Duration.ofMinutes(2);
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_OTP_ATTEMPTS = 5;

    @Qualifier("authRedisTemplate")
    private final StringRedisTemplate authRedis;

    private final RedisCircuitBreaker circuitBreaker;

    @Override
    public void saveOtp(String email, String otpCode) {
        circuitBreaker.write("otp.save",
                () -> authRedis.opsForValue().set(otpKey(email), otpCode, OTP_TTL));
    }

    @Override
    public Optional<String> findOtp(String email) {
        return Optional.ofNullable(circuitBreaker.read("otp.find",
                () -> authRedis.opsForValue().get(otpKey(email)), null));
    }

    @Override
    public void deleteOtp(String email) {
        circuitBreaker.write("otp.delete", () -> {
            authRedis.delete(otpKey(email));
            authRedis.delete(attemptKey(email));
        });
    }

    @Override
    public long acquireResendSlot(String email) {
        String key = cooldownKey(email);
        Boolean acquired = circuitBreaker.read("otp.cooldown",
                () -> authRedis.opsForValue().setIfAbsent(key, "1", RESEND_COOLDOWN), Boolean.TRUE);

        if (Boolean.TRUE.equals(acquired)) {
            return 0;
        }
        Long ttl = circuitBreaker.read("otp.cooldown.ttl",
                () -> authRedis.getExpire(key, TimeUnit.SECONDS), null);
        return ttl != null && ttl > 0 ? ttl : RESEND_COOLDOWN.getSeconds();
    }

    @Override
    public boolean isAttemptExceeded(String email) {
        String key = attemptKey(email);
        Long attempts = circuitBreaker.read("otp.attempt",
                () -> {
                    Long value = authRedis.opsForValue().increment(key);
                    if (value != null && value == 1L) {
                        authRedis.expire(key, ATTEMPT_WINDOW);
                    }
                    return value;
                }, null);
        return attempts != null && attempts > MAX_OTP_ATTEMPTS;
    }

    @Override
    public int maxOtpAttempts() {
        return MAX_OTP_ATTEMPTS;
    }

    @Override
    public void savePasswordResetToken(String token, Long userId) {
        circuitBreaker.write("pwreset.save", () -> {
            String previous = authRedis.opsForValue().get(resetUserKey(userId));
            if (previous != null) {
                authRedis.delete(resetTokenKey(previous));
            }
            authRedis.opsForValue().set(resetTokenKey(token), String.valueOf(userId), RESET_TOKEN_TTL);
            authRedis.opsForValue().set(resetUserKey(userId), token, RESET_TOKEN_TTL);
        });
    }

    @Override
    public Optional<Long> findUserIdByResetToken(String token) {
        String userId = circuitBreaker.read("pwreset.find",
                () -> authRedis.opsForValue().get(resetTokenKey(token)), null);
        if (userId == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(userId));
        } catch (NumberFormatException e) {
            log.warn("Giá trị userId trong reset token không hợp lệ: {}", userId);
            return Optional.empty();
        }
    }

    @Override
    public void deletePasswordResetToken(String token, Long userId) {
        circuitBreaker.write("pwreset.delete", () -> {
            authRedis.delete(resetTokenKey(token));
            if (userId != null) {
                authRedis.delete(resetUserKey(userId));
            }
        });
    }

    @Override
    public void denyToken(String jti, Duration remainingLifetime) {
        if (jti == null || remainingLifetime == null || remainingLifetime.isNegative()
                || remainingLifetime.isZero()) {
            return;
        }
        circuitBreaker.write("denylist.add",
                () -> authRedis.opsForValue().set(
                        CacheNames.KEY_DENYLIST + jti, "1", remainingLifetime));
    }

    @Override
    public boolean isTokenDenied(String jti) {
        if (jti == null) {
            return false;
        }
        return Boolean.TRUE.equals(circuitBreaker.read("denylist.check",
                () -> authRedis.hasKey(CacheNames.KEY_DENYLIST + jti), Boolean.FALSE));
    }


    private String otpKey(String email) {
        return CacheNames.KEY_OTP + normalize(email);
    }

    private String cooldownKey(String email) {
        return CacheNames.KEY_OTP_COOLDOWN + normalize(email);
    }

    private String attemptKey(String email) {
        return CacheNames.KEY_OTP_ATTEMPT + normalize(email);
    }

    private String resetTokenKey(String token) {
        return CacheNames.KEY_PWRESET + token;
    }

    private String resetUserKey(Long userId) {
        return CacheNames.KEY_PWRESET_USER + userId;
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
