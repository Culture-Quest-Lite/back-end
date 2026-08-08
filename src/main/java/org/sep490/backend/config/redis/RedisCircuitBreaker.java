package org.sep490.backend.config.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Slf4j
@Component
public class RedisCircuitBreaker {

    private static final Duration OPEN_DURATION = Duration.ofSeconds(30);

    private final AtomicLong openUntil = new AtomicLong(0);
    public boolean isOpen() {
        return System.currentTimeMillis() < openUntil.get();
    }

    private void trip(String operation, Exception e) {
        boolean wasClosed = !isOpen();
        openUntil.set(System.currentTimeMillis() + OPEN_DURATION.toMillis());
        if (wasClosed) {
            log.warn("Redis lỗi ở [{}], tạm bỏ qua Redis trong {} giây: {}",
                    operation, OPEN_DURATION.getSeconds(), e.getMessage());
        }
    }

    public <T> T read(String operation, Supplier<T> action, T fallback) {
        if (isOpen()) {
            return fallback;
        }
        try {
            return action.get();
        } catch (Exception e) {
            trip(operation, e);
            return fallback;
        }
    }

    public void write(String operation, Runnable action) {
        if (isOpen()) {
            return;
        }
        try {
            action.run();
        } catch (Exception e) {
            trip(operation, e);
        }
    }
}
