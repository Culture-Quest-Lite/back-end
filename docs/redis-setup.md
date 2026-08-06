# Hướng dẫn tích hợp Redis — Code từng bước

> Tài liệu này để **bạn tự code**. Mỗi bước có code đầy đủ, copy-paste được, kèm cách kiểm chứng.
> Làm tuần tự, verify xong mới sang bước sau.

## Tiến độ

- [x] **Bước 1–6 (Phase 0 — hạ tầng): ĐÃ XONG, đã verify** — xem "Kết quả verify bước 6".
- [x] **Bước 7 (Phase 1 — master data): ĐÃ XONG, đã verify** — xem "Kết quả verify bước 7".
- [x] **Bước 8–10 (Phase 2 — dashboard + API ngoài): ĐÃ XONG, đã verify** — xem "Kết quả verify bước 8–10".
- [x] **Bước 11 (Phase 3 — rating + check-in): ĐÃ XONG, đã verify** — xem "Kết quả verify bước 11".
      Bước này bổ sung `RedisCircuitBreaker` (mục 5.6) — **bắt buộc** cho mọi chỗ dùng `RedisTemplate` trực tiếp.
- [x] **Bước 12–13 (Phase 6 — leaderboard + counter user): ĐÃ XONG, đã verify** — xem "Kết quả verify bước 12–13".
- [x] **Bước 14 (Phase 4 — counter mạng xã hội): ĐÃ XONG, đã verify** — xem "Kết quả verify bước 14".
- [x] **Bước 15–17 (Phase 5 — auth): ĐÃ XONG, đã verify** — xem "Kết quả verify bước 15–17".
- [x] **Bước 18 (Phase 7 — geo): ĐÃ XONG, đã verify** — xem "Kết quả verify bước 18".

**→ TOÀN BỘ 18 BƯỚC ĐÃ HOÀN THÀNH.**

## Context

Backend hiện **không có lớp cache nào** (không `spring-boot-starter-cache`, không `@EnableCaching`, không `@Cacheable` trong toàn bộ `src/`). Sáu vấn đề thực tế:

| Vấn đề | Vị trí |
|---|---|
| Đếm like/comment/share bằng stream toàn bộ collection lazy | [PostMapper.java:39-42](src/main/java/org/sep490/backend/module/social/mapper/PostMapper.java#L39-L42) |
| Rating summary query lại ở mọi listing/detail | [RatingSummaryApplier.java](src/main/java/org/sep490/backend/module/content/service/impl/RatingSummaryApplier.java) |
| Dashboard chạy 7 query aggregate mỗi lần load | [AdminDashboardServiceImpl.java:86-96](src/main/java/org/sep490/backend/module/admin/service/impl/AdminDashboardServiceImpl.java#L86-L96) |
| `getCurrentUser()` query DB mỗi request | [UserServiceImpl.java:341](src/main/java/org/sep490/backend/module/user/service/impl/UserServiceImpl.java#L341) |
| Gọi API trả phí lặp lại (Goong, Groq) | [GoongDistanceServiceImpl.java:29](src/main/java/org/sep490/backend/module/planner/service/impl/GoongDistanceServiceImpl.java#L29) |
| Bảng `email_otps`, `password_reset_tokens` phình vô hạn | không có cleanup job |

**Ràng buộc:** một instance EC2, Redis chạy trong `docker-compose.yml`.

---

## 7 nguyên tắc — đọc kỹ trước khi code

1. **Redis không bao giờ là nguồn sự thật.** Xoá sạch Redis phải không mất dữ liệu nghiệp vụ. (Ngoại lệ có chủ ý: OTP ở Bước 12.)
2. **Redis chết → app vẫn chạy.** `CacheErrorHandler` ở Bước 5 là phần quan trọng nhất cả tài liệu.
3. **Không cache entity JPA** (`User`, `Post`, `Hotspot`) — có lazy association, sẽ ném `LazyInitializationException`. Chỉ cache DTO/projection/kiểu nguyên thuỷ.
4. **Không cache `Page<T>`.** `PageImpl` không deserialize được từ JSON. Cache `List<T>` + `long total` rồi dựng lại.
5. **`@Cacheable` không chạy khi self-invocation** (gọi `this.method()` trong cùng class) — do cơ chế proxy.
6. **`@CacheEvict` trong `@Transactional` chạy TRƯỚC commit** → có cửa sổ race. Chỗ nhạy cảm dùng `@TransactionalEventListener(AFTER_COMMIT)`.
7. **Luôn dùng hằng số tên cache**, không hard-code string.

---

# PHASE 0 — HẠ TẦNG

## Bước 1: `pom.xml`

Thêm vào `<dependencies>` (sau `spring-boot-starter-data-jpa`):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
```

`commons-pool2` bắt buộc để `spring.data.redis.lettuce.pool.*` có tác dụng — thiếu nó cấu hình pool bị bỏ qua âm thầm.

**Tiện tay:** [pom.xml:30-31](pom.xml#L30-L31) có `<java.version>21</java.version>` lặp 2 lần → xoá 1 dòng.

## Bước 2: Biến môi trường

Thêm vào `src/main/resources/.env`:

```
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=chon-mot-mat-khau-manh-o-day
```

### ⚠️ Bẫy `.env` root — đã vấp phải khi verify

[DotEnvConfig.java:9](src/main/java/org/sep490/backend/config/security/DotEnvConfig.java#L9) dùng `Dotenv.configure().filename(".env")`, mặc định tìm ở **thư mục làm việc** (root repo), **không phải** classpath. Trước đây repo không có `.env` ở root nên nó fallback về classpath → đọc `src/main/resources/.env`.

Hệ quả: **nếu tạo `.env` root chỉ với vài biến Redis, nó sẽ CHE MẤT file resources** và app chết ngay với lỗi:
```
PlaceholderResolutionException: Circular placeholder reference 'APPLICATION_NAME'
```

Cách đúng — `.env` root phải là **bản sao đầy đủ** của `src/main/resources/.env`, cộng thêm biến chỉ compose cần:

```bash
cp src/main/resources/.env .env
# rồi thêm vào cuối .env:
KC_PORT=8180
KC_ADMIN=admin
KC_ADMIN_PASSWORD=admin
```

**Từ nay sửa biến môi trường phải sửa ở CẢ HAI file**, nếu không sẽ lệch. Cả hai đều đã nằm trong `.gitignore` (dòng 36–37).

Lưu ý `REDIS_HOST`: để `localhost` trong cả hai file. Khi chạy bằng Docker, compose đã override `REDIS_HOST: redis` trong service `backend` rồi.

> **Bẫy chung:** `DotEnvConfig.loadEnv()` chạy **trước** `SpringApplication.run`, đẩy `.env` vào `System.setProperty`. Đa số `${VAR}` trong `application.yml` không có default → thiếu biến là app chết lúc khởi động. Vì vậy ở Bước 3 **luôn viết dạng có default**.

## Bước 3: `application.yml`

Thêm vào trong block `spring:` (ngang hàng với `datasource`, `jpa`):

```yaml
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0
      timeout: 2s
      connect-timeout: 2s
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 2
  cache:
    type: redis
```

Thêm vào block `app:` đã có sẵn (dưới `points`, `firebase`):

```yaml
app:
  cache:
    enabled: ${CACHE_ENABLED:true}
  redis:
    auth-database: 1
```

Thêm ở cuối file (Redis down không được làm healthcheck container fail → tránh Docker restart vòng lặp):

```yaml
management:
  health:
    redis:
      enabled: false
```

## Bước 4: `docker-compose.yml`

Thêm `redis_data` vào block `volumes:` ở đầu file:

```yaml
volumes:
  postgres_data:
  redis_data:
```

Thêm service `redis` (đặt trước service `backend`):

```yaml
  redis:
    image: redis:7.4-alpine
    container_name: culture-quest-redis
    restart: unless-stopped
    command:
    environment:
      # BẮT BUỘC: $$REDIS_PASSWORD trong healthcheck được expand BÊN TRONG container,
      # nên container phải tự có biến này. Thiếu nó -> healthcheck luôn WRONGPASS -> unhealthy.
      REDIS_PASSWORD: ${REDIS_PASSWORD}
    command:
      - redis-server
      - --requirepass
      - ${REDIS_PASSWORD}
      - --maxmemory
      - 256mb
      - --maxmemory-policy
      - allkeys-lru
      - --appendonly
      - "yes"
    ports:
      - "127.0.0.1:${REDIS_PORT:-6379}:6379"
    volumes:
      - redis_data:/data
    networks:
      - culture-quest-net
    healthcheck:
      test: ["CMD-SHELL", "redis-cli -a \"$$REDIS_PASSWORD\" --no-auth-warning ping | grep PONG"]
      interval: 10s
      timeout: 5s
      retries: 5
```

> ⚠️ **Bẫy healthcheck — đã vấp phải.** Nếu quên block `environment:` ở trên, container vẫn **chạy được** nhưng healthcheck luôn fail với `AUTH failed: WRONGPASS`, container bị đánh dấu `unhealthy`. Hậu quả: khi deploy, `depends_on: redis: condition: service_healthy` sẽ **treo vĩnh viễn**. Kiểm tra bằng:
> ```powershell
> docker inspect culture-quest-redis --format='{{json .State.Health}}'
> ```

Trong service `backend`, thêm vào `environment:` (giống pattern `DB_HOST: postgres` đã có):

```yaml
      REDIS_HOST: redis
```

Và thêm vào `depends_on:`:

```yaml
      redis:
        condition: service_healthy
```

**Về `allkeys-lru`:** LRU có thể evict cả key OTP ở `db 1` khi đầy bộ nhớ. Với 256MB và quy mô đồ án, xác suất chạm trần gần như bằng 0, và AOF đảm bảo restart không mất dữ liệu. Chọn nó thay `noeviction` (vốn làm **mọi lệnh ghi fail** khi đầy — tệ hơn nhiều).

**Vấn đề CI/CD:** [.github/workflows/deploy.yml](.github/workflows/deploy.yml) deploy bằng `docker compose up -d --no-deps --no-build backend`. Cờ `--no-deps` khiến service `redis` mới **không tự khởi động**. Xử lý: SSH vào EC2 chạy `docker compose up -d redis` **một lần** trước khi merge. (Hoặc sửa script thành `up -d --no-build redis backend`.)

## Bước 5: Package `config/redis/`

Tạo thư mục `src/main/java/org/sep490/backend/config/redis/`.

### 5.1 — `CacheNames.java`

```java
package org.sep490.backend.config.redis;

import java.time.Duration;

/**
 * Tên cache và TTL tập trung một chỗ.
 * Mọi @Cacheable/@CacheEvict phải dùng hằng số ở đây, không hard-code string.
 */
public final class CacheNames {

    private CacheNames() {
    }

    // Phase 1 - master data
    public static final String LEVELS = "levels";
    public static final String LEVEL_BY_XP = "levelByXp";
    public static final String TAGS = "tags";
    public static final String SUBSCRIPTION_PLANS = "subscriptionPlans";

    // Phase 2 - dashboard + API ngoài
    public static final String ADMIN_DASHBOARD = "adminDashboard";
    public static final String CURATOR_DASHBOARD = "curatorDashboard";
    public static final String GOONG_MATRIX = "goongMatrix";
    public static final String AI_RERANK = "aiRerank";

    // Phase 6 - user
    public static final String LEADERBOARD = "leaderboard";
    public static final String MY_RANK = "myRank";
    public static final String USER_BY_KEYCLOAK = "userByKeycloak";

    // Phase 7 - content + geo
    public static final String GEO_IN_VIETNAM = "geoInVietnam";
    public static final String GEO_NEARBY = "geoNearby";
    public static final String HOTSPOT_DETAIL = "hotspotDetail";
    public static final String ROUTE_DETAIL = "routeDetail";
    public static final String STORY_DETAIL = "storyDetail";

    // TTL
    public static final Duration TTL_MASTER_DATA = Duration.ofHours(6);
    public static final Duration TTL_TAGS = Duration.ofHours(1);
    public static final Duration TTL_DASHBOARD = Duration.ofMinutes(3);
    public static final Duration TTL_GOONG = Duration.ofDays(30);
    public static final Duration TTL_AI = Duration.ofHours(24);
    public static final Duration TTL_LEADERBOARD = Duration.ofSeconds(60);
    public static final Duration TTL_USER = Duration.ofMinutes(30);
    public static final Duration TTL_GEO_STATIC = Duration.ofDays(30);
    public static final Duration TTL_GEO_NEARBY = Duration.ofMinutes(30);
    public static final Duration TTL_CONTENT_DETAIL = Duration.ofMinutes(15);

    // Key prefix cho thao tác Redis thủ công (không qua CacheManager)
    public static final String KEY_RATING = "rating:";
    public static final String KEY_CHECKIN_USER = "checkin:user:";
    public static final String KEY_POST_COUNTS = "post:%d:counts";
    public static final String KEY_POST_LIKERS = "post:%d:likers";
    public static final String KEY_USER_COUNTS = "user:%d:counts";
    public static final String KEY_NOTIF_UNREAD = "notif:unread:";
    public static final String KEY_OTP = "otp:";
    public static final String KEY_OTP_COOLDOWN = "otp:cooldown:";
    public static final String KEY_OTP_ATTEMPT = "otp:attempt:";
    public static final String KEY_PWRESET = "pwreset:";
    public static final String KEY_PWRESET_USER = "pwreset:user:";
    public static final String KEY_DENYLIST = "denylist:jti:";
    public static final String KEY_KC_ADMIN_TOKEN = "kc:admin-token";
}
```

### 5.2 — `RedisConfig.java`

```java
package org.sep490.backend.config.redis;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * ObjectMapper RIÊNG cho Redis.
     * KHÔNG được sửa bean ObjectMapper ở JacksonConfig: bean đó phục vụ response HTTP,
     * bật activateDefaultTyping lên nó sẽ khiến mọi JSON trả về kèm field "@class".
     */
    @Bean("redisObjectMapper")
    public ObjectMapper redisObjectMapper(ObjectMapper appObjectMapper) {
        ObjectMapper mapper = appObjectMapper.copy();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
                // EVERYTHING chứ KHÔNG phải NON_FINAL — xem cảnh báo ngay bên dưới.
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY);
        return mapper;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            @Qualifier("redisObjectMapper") ObjectMapper redisObjectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
```

`JavaTimeModule` bắt buộc vì DTO dùng `LocalDateTime` (`Asia/Ho_Chi_Minh`, xem `config/time/AppTimeConfig.java`). `activateDefaultTyping` cần để deserialize đúng kiểu — DTO dự án là Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`, đã có constructor rỗng nên thoả điều kiện.

> ⚠️ **`EVERYTHING` chứ KHÔNG phải `NON_FINAL` — đã vấp phải ở Bước 9.**
>
> `NON_FINAL` chỉ ghi field `@class` cho class **không final**. Nhưng **`record` trong Java là class final**, nên record được ghi ra JSON **không có `@class`**:
> ```json
> {"distanceMeters":[[0.0,1500.0]],"fromFallback":false}
> ```
> Đọc lại từ Redis sẽ ném:
> ```
> InvalidTypeIdException: Could not resolve subtype of [simple type, class java.lang.Object]:
> missing type id property '@class'
> ```
> Hậu quả: `instanceof DistanceMatrixResult` luôn `false` → **cache không bao giờ hit**, lặng lẽ gọi API trả phí mỗi lần. Ảnh hưởng trực tiếp `DistanceMatrixResult` (Bước 9) và `HotspotPickList` (Bước 10) — cả hai đều là `record`.
>
> `NON_FINAL` còn một lỗi nữa: **`Long` bị đọc lại thành `Integer`** → `ClassCastException` ở mọi cache lưu số.
>
> Kết quả kiểm chứng thực tế:
>
> | Kiểu dữ liệu | `NON_FINAL` | `EVERYTHING` |
> |---|---|---|
> | DTO Lombok (LocalDateTime + enum) | OK | OK |
> | `List<DTO>` | OK | OK |
> | `record` + `double[][]` | **LỖI** | OK |
> | `String` | OK | OK |
> | `Long` | **thành `Integer`** | OK |
>
> Với `EVERYTHING`, JSON trong Redis sẽ có dạng `"tagId":["java.lang.Long",6]` — đó là cách nó giữ đúng kiểu số.

### 5.3 — `RedisCacheConfig.java`

```java
package org.sep490.backend.config.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Qualifier("redisObjectMapper") ObjectMapper redisObjectMapper) {

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(redisObjectMapper)));

        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        configs.put(CacheNames.LEVELS, base.entryTtl(CacheNames.TTL_MASTER_DATA));
        configs.put(CacheNames.LEVEL_BY_XP, base.entryTtl(CacheNames.TTL_MASTER_DATA));
        configs.put(CacheNames.TAGS, base.entryTtl(CacheNames.TTL_TAGS));
        configs.put(CacheNames.SUBSCRIPTION_PLANS, base.entryTtl(CacheNames.TTL_MASTER_DATA));
        configs.put(CacheNames.ADMIN_DASHBOARD, base.entryTtl(CacheNames.TTL_DASHBOARD));
        configs.put(CacheNames.CURATOR_DASHBOARD, base.entryTtl(CacheNames.TTL_DASHBOARD));
        configs.put(CacheNames.GOONG_MATRIX, base.entryTtl(CacheNames.TTL_GOONG));
        configs.put(CacheNames.AI_RERANK, base.entryTtl(CacheNames.TTL_AI));
        configs.put(CacheNames.LEADERBOARD, base.entryTtl(CacheNames.TTL_LEADERBOARD));
        configs.put(CacheNames.MY_RANK, base.entryTtl(CacheNames.TTL_LEADERBOARD));
        configs.put(CacheNames.USER_BY_KEYCLOAK, base.entryTtl(CacheNames.TTL_USER));
        configs.put(CacheNames.GEO_IN_VIETNAM, base.entryTtl(CacheNames.TTL_GEO_STATIC));
        configs.put(CacheNames.GEO_NEARBY, base.entryTtl(CacheNames.TTL_GEO_NEARBY));
        configs.put(CacheNames.HOTSPOT_DETAIL, base.entryTtl(CacheNames.TTL_CONTENT_DETAIL));
        configs.put(CacheNames.ROUTE_DETAIL, base.entryTtl(CacheNames.TTL_CONTENT_DETAIL));
        configs.put(CacheNames.STORY_DETAIL, base.entryTtl(CacheNames.TTL_CONTENT_DETAIL));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(configs)
                .build();
    }
}
```

### 5.4 — `CacheErrorConfig.java` ⭐ QUAN TRỌNG NHẤT

```java
package org.sep490.backend.config.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis chết thì app phải chạy tiếp (chậm hơn), tuyệt đối không trả 500 cho client.
 * Mọi lỗi cache chỉ ghi log rồi để luồng đi thẳng xuống DB.
 */
@Slf4j
@Configuration
public class CacheErrorConfig implements CachingConfigurer {

    @Override
    @Bean
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Lỗi đọc cache [{}] key={} — bỏ qua, truy vấn DB", cache.getName(), key, e);
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Lỗi ghi cache [{}] key={} — bỏ qua", cache.getName(), key, e);
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Lỗi xoá cache [{}] key={} — bỏ qua", cache.getName(), key, e);
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Lỗi clear cache [{}] — bỏ qua", cache.getName(), e);
            }
        };
    }
}
```

### 5.5 — `AuthRedisConfig.java` (logical DB 1 cho OTP/denylist)

```java
package org.sep490.backend.config.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis DB 1: dữ liệu auth (OTP, reset token, denylist, rate limit).
 * Tách khỏi DB 0 (cache thuần) để soi/flush độc lập.
 */
@Configuration
public class AuthRedisConfig {

    /**
     * autowireCandidate = false: bean này CHỈ dùng nội bộ cho authRedisTemplate.
     * Nếu để nó là autowire candidate, nó sẽ tranh chấp với factory auto-config khi
     * Spring tự chọn RedisConnectionFactory -> cache ghi nhầm DB 1 thay vì DB 0.
     */
    @Bean(name = "authRedisConnectionFactory", autowireCandidate = false)
    public RedisConnectionFactory authRedisConnectionFactory(
            RedisProperties properties,
            @Value("${app.redis.auth-database:1}") int authDatabase) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(properties.getHost());
        config.setPort(properties.getPort());
        config.setDatabase(authDatabase);
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            config.setPassword(properties.getPassword());
        }
        return new LettuceConnectionFactory(config);
    }

    /**
     * Gọi thẳng authRedisConnectionFactory(...) thay vì inject qua @Qualifier,
     * vì autowireCandidate = false chặn cả việc resolve bằng qualifier.
     * Spring vẫn đảm bảo singleton nhờ cơ chế proxy của @Configuration.
     */
    @Bean("authRedisTemplate")
    public StringRedisTemplate authRedisTemplate(
            RedisProperties properties,
            @Value("${app.redis.auth-database:1}") int authDatabase) {
        return new StringRedisTemplate(authRedisConnectionFactory(properties, authDatabase));
    }
}
```

> ⚠️ **Bẫy tranh chấp bean — đã vấp phải ở Bước 7.** Nếu khai `@Bean("authRedisConnectionFactory")` trả về kiểu `LettuceConnectionFactory` **mà không có `autowireCandidate = false`**, Spring sẽ chọn nhầm nó khi `RedisCacheConfig` và `RedisConfig` xin một `RedisConnectionFactory`. Hậu quả: **toàn bộ cache ghi vào DB 1 thay vì DB 0**, dù `application.yml` khai `database: 0`. Triệu chứng rất khó thấy — app chạy bình thường, cache vẫn hoạt động, chỉ nằm sai DB.
>
> Kiểm tra bằng:
> ```powershell
> docker exec -it culture-quest-redis redis-cli -a "mat-khau" --no-auth-warning -n 0 dbsize
> docker exec -it culture-quest-redis redis-cli -a "mat-khau" --no-auth-warning -n 1 dbsize
> ```
> Sau khi gọi API có `@Cacheable`, **DB 0 phải > 0 và DB 1 phải = 0**.
>
> Lưu ý: đừng thử "sửa" bằng `@Qualifier("redisConnectionFactory")` ở phía tiêu thụ — bean auto-config của Boot **không mang tên đó**, sẽ lỗi `NoSuchBeanDefinitionException`.

### 5.6 — `RedisCircuitBreaker.java` ⭐ BẮT BUỘC cho mọi chỗ dùng `RedisTemplate` trực tiếp

> Mục này được thêm sau khi phát hiện lỗi thật ở Bước 11 — xem giải thích ngay dưới code.

```java
package org.sep490.backend.config.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Ngắt mạch cho các thao tác Redis thủ công (không đi qua CacheManager).
 *
 * Khi Redis chết, MỖI lệnh phải chờ hết spring.data.redis.timeout (2s).
 * Một request gọi Redis 12 lần -> 24 giây chờ vô ích.
 * Sau lần lỗi đầu tiên, mọi lệnh trong OPEN_DURATION bị bỏ qua ngay,
 * nên tệ nhất mỗi request chỉ tốn 1 lần timeout.
 */
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

    /** Thao tác đọc. Mạch mở hoặc lỗi thì trả fallback. */
    public <T> T read(String operation, Supplier<T> action, T fallback) {
        if (isOpen()) return fallback;
        try {
            return action.get();
        } catch (Exception e) {
            trip(operation, e);
            return fallback;
        }
    }

    /** Thao tác ghi. Mạch mở hoặc lỗi thì bỏ qua im lặng. */
    public void write(String operation, Runnable action) {
        if (isOpen()) return;
        try {
            action.run();
        } catch (Exception e) {
            trip(operation, e);
        }
    }
}
```

Cách dùng — thay `try/catch` quanh mỗi lệnh Redis:
```java
// Thay vì:
try { cached = redisTemplate.opsForValue().get(key); }
catch (Exception e) { log.warn(...); }

// Dùng:
Object cached = circuitBreaker.read("ten.thao.tac",
        () -> redisTemplate.opsForValue().get(key), null);
```

> ⚠️ **Vì sao BẮT BUỘC — lỗi thật đo được ở Bước 11.**
>
> `CacheErrorHandler` (mục 5.4) **chỉ bảo vệ `@Cacheable`**. Với `RedisTemplate` trực tiếp, `try/catch` giữ cho app không sập nhưng **không cứu được độ trễ**: mỗi lệnh vẫn chờ đủ `timeout: 2s`.
>
> Đo thực tế trên `GET /api/v1/hotspots` khi Redis chết:
>
> | Cách làm | Thời gian | Kết quả |
> |---|---|---|
> | Ghi từng key trong vòng lặp (44 key) | **88.57s** | `HTTP 000` — client bỏ cuộc |
> | Gộp `multiSet` + pipeline (12 lần gọi) | 48.35s | 200 nhưng vẫn quá chậm |
> | **+ `RedisCircuitBreaker`** | **2.15s** | 200 — chỉ 1 lần timeout |
> | Request tiếp theo (mạch đang mở) | **0.097s** | 200 — bỏ qua Redis ngay |
>
> Hai bài học:
> 1. **Không bao giờ gọi Redis trong vòng lặp** — gộp bằng `multiGet`/`multiSet`/pipeline.
> 2. **Luôn bọc `RedisCircuitBreaker`** quanh thao tác `RedisTemplate` thủ công.
>
> Nên áp dụng ngược lại cho Bước 9 (Goong) và Bước 10 (AI rerank) nếu bạn đã code trước khi đọc mục này.

## Bước 6: Verify Phase 0 — BẮT BUỘC làm trước khi đi tiếp

```powershell
.\mvnw.cmd clean compile
docker compose up -d redis
docker exec -it culture-quest-redis redis-cli -a "mat-khau-cua-ban" --no-auth-warning ping
```
Phải trả `PONG`.

Kiểm tra cấu hình runtime đã áp dụng đúng:
```powershell
docker exec -it culture-quest-redis redis-cli -a "mat-khau-cua-ban" --no-auth-warning config get maxmemory-policy
docker exec -it culture-quest-redis redis-cli -a "mat-khau-cua-ban" --no-auth-warning config get appendonly
```

**Kiểm chứng app THẬT SỰ kết nối được Redis.** Dòng `Started BackEndApplication` chưa đủ — Lettuce kết nối lười nên app vẫn khởi động OK cả khi Redis chết. Bật health redis tạm thời:
```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--management.health.redis.enabled=true --management.endpoint.health.show-details=always"
curl http://localhost:8080/actuator/health
```
JSON trả về phải có:
```json
"redis": { "status": "UP", "details": { "version": "7.4.10" } }
```

**Bài test quan trọng nhất:**
```powershell
docker stop culture-quest-redis
```
Gọi lại vài endpoint (`/api/tags`, `/api/v1/hotspots`, `/api/users/leaderboard`) — **tất cả phải vẫn trả 200**.

```powershell
docker start culture-quest-redis
```

> ⚠️ **Ở bước này bài test trên CHƯA chứng minh được `CacheErrorHandler`.** Chưa có `@Cacheable` nào nên chưa lệnh Redis nào chạy trong luồng request. Nó mới chỉ chứng minh app khởi động và phục vụ bình thường khi không có Redis.
>
> **Test thật sự có ý nghĩa là sau Bước 7.** Lúc đó chạy lại và phải thấy log:
> ```
> Lỗi đọc cache [levels] key=... — bỏ qua, truy vấn DB
> ```
> Nếu endpoint trả 500 thay vì log warning → `CacheErrorConfig` chưa đúng, phải sửa trước khi đi tiếp.

### Kết quả verify bước 6 (đã chạy thật ngày 2026-08-04)

| Kiểm tra | Kết quả |
|---|---|
| `mvnw clean compile` | BUILD SUCCESS |
| Redis container | Up, `PONG` với mật khẩu |
| `maxmemory` / policy / AOF | 268435456 / `allkeys-lru` / `yes` |
| DB 0 và DB 1 tách biệt | Ghi DB 1, đọc DB 0 không thấy — đúng |
| App khởi động | `Started BackEndApplication in 18.9s` |
| **App kết nối Redis** | `"redis": {"status":"UP","version":"7.4.10"}` |
| **Tắt Redis → app vẫn chạy** | `/actuator/health`, `/api/tags`, `/api/v1/hotspots`, `/api/users/leaderboard`, `/api/v1/stories` đều 200 |

**4 lỗi đã phát hiện và sửa trong lúc verify:**
1. `spring.cache.type` bị lồng nhầm vào `spring.data` → đã tách ra ngang hàng với `data`
2. `import io.lettuce.core.json.JsonType` thừa trong `RedisConfig.java` → đã xoá
3. `${REDIS_PASSWORD}` thiếu default → đổi thành `${REDIS_PASSWORD:}`
4. Thiếu `.env` root → đã tạo dạng superset (xem Bước 2)

**Lỗi CÓ SẴN, không liên quan Redis** (đã đối chứng: kết quả giống hệt khi Redis bật và tắt):
`/api/v1/categories`, `/api/v1/routes`, `/api/v2/routes`, `/api/posts` trả HTTP 500.

**Hạn chế đã biết:** không viết được `@SpringBootTest` cho Redis vì dự án thiếu `src/test/resources/.env` — `BackEndApplicationTests` cũng vướng đúng lỗi này. Muốn có integration test thì phải tạo file đó trước.

---

# PHASE 1 — MASTER DATA (bước 7)

## Bước 7: Level, Tag, SubscriptionPlan

### 7.1 — `LevelServiceImpl`

```java
import org.sep490.backend.config.redis.CacheNames;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

@Override
@Cacheable(value = CacheNames.LEVELS, key = "'all'")
public List<LevelResponse> getAllLevels() { /* giữ nguyên thân hàm */ }

@Override
@Cacheable(value = CacheNames.LEVELS, key = "#levelId")
public LevelResponse getLevelById(Long levelId) { /* giữ nguyên */ }

// Gắn lên createLevel / updateLevel / deleteLevel:
@Caching(evict = {
        @CacheEvict(value = CacheNames.LEVELS, allEntries = true),
        @CacheEvict(value = CacheNames.LEVEL_BY_XP, allEntries = true)
})
```

### 7.2 — `TagServiceImpl`

`getAllWithFilter(...)` trả `Page<TagResponse>` → **không cache trực tiếp** (nguyên tắc #4). Ở bước này chỉ cache `getDetail`:

```java
@Override
@Cacheable(value = CacheNames.TAGS, key = "#id")
public TagResponse getDetail(Long id) { /* giữ nguyên */ }

// Trên create/update/delete tag:
@CacheEvict(value = CacheNames.TAGS, allEntries = true)
```

Muốn cache cả danh sách thì tách một method trả `List<TagResponse>` (bảng tag nhỏ, lấy hết được) rồi lọc/phân trang trên bộ nhớ — làm sau cũng được.

### 7.3 — `SubscriptionPlanServiceImpl`

```java
@Override
@Cacheable(value = CacheNames.SUBSCRIPTION_PLANS, key = "#type.name()")
public List<SubscriptionPlanResponse> getActivePlanByType(PlanType type) { /* giữ nguyên */ }

// Trên CRUD plan và plan rule:
@CacheEvict(value = CacheNames.SUBSCRIPTION_PLANS, allEntries = true)
```

### Verify bước 7

Endpoint công khai đi vào đúng method có `@Cacheable` là `GET /api/tags/{id}`.
(`GET /api/tags` gọi `getAllWithFilter` — **không** có cache; `/api/gamification/levels` cần auth.)

```powershell
$R = 'docker exec culture-quest-redis redis-cli -a "mat-khau" --no-auth-warning'
# 1. Xoá sạch cache
docker exec culture-quest-redis redis-cli -a "mat-khau" --no-auth-warning -n 0 flushdb
# 2. Gọi 2 lần, so thời gian
curl -w "%{time_total}s`n" -o $null -s http://localhost:8080/api/tags/6
curl -w "%{time_total}s`n" -o $null -s http://localhost:8080/api/tags/6
# 3. Kiểm tra key + TTL
docker exec culture-quest-redis redis-cli -a "mat-khau" --no-auth-warning -n 0 keys "*"
docker exec culture-quest-redis redis-cli -a "mat-khau" --no-auth-warning -n 0 ttl "tags::6"
```

Công cụ mạnh nhất khi nghi ngờ cache không chạy — xem lệnh Redis theo thời gian thực:
```powershell
docker exec -it culture-quest-redis redis-cli -a "mat-khau" --no-auth-warning monitor
```
Phải thấy `GET "tags::6"` rồi `SET "tags::6" ... "PX" "3600000"`.

### Kết quả verify bước 7 (đã chạy thật ngày 2026-08-04)

| Kiểm tra | Kết quả |
|---|---|
| Cache ghi đúng DB | DB 0 có `tags::6`, DB 1 = 0 key |
| TTL | 3598s (~1 giờ, khớp `TTL_TAGS`) |
| Tốc độ MISS → HIT | 1.449s → 0.079s (**nhanh hơn 18 lần**) |
| Serializer | JSON có `"@class"` → `activateDefaultTyping` OK |
| `@CacheEvict` khi PUT | `KEYS "tags::*"` chạy, DB 0 về 0 key |
| Nạp lại sau evict | GET lại → DB 0 có 1 key |
| **Redis chết → app vẫn 200** | `/api/tags/6`, `/api/tags/5`, `/api/tags` đều 200 |
| **`CacheErrorHandler` chạy đúng** | log `Lỗi đọc cache [tags] key=6 — bỏ qua, truy vấn DB` |
| Redis sống lại | App tự kết nối lại, cache ghi bình thường |

**2 lỗi hạ tầng phát hiện ở bước này** (cả hai đã sửa và ghi vào Bước 4 / 5.5):
1. Healthcheck Redis luôn `WRONGPASS` → container `unhealthy` → deploy sẽ treo ở `depends_on`
2. `authRedisConnectionFactory` tranh chấp bean → **cache ghi nhầm vào DB 1**

**Kinh nghiệm khi verify:** đừng dùng `keys "*" | wc -l` để đếm — dòng trống bị đếm thành 1, gây hiểu nhầm là eviction không chạy. Dùng `dbsize`.

---

# PHASE 2 — DASHBOARD + API NGOÀI (bước 8–10)

## Bước 8: Dashboard

Chỉ TTL, không cần evict — số liệu trễ 3 phút hoàn toàn chấp nhận được, đổi lại tiết kiệm 7–8 query aggregate mỗi lần F5.

`AdminDashboardServiceImpl.getDashboard()` ([dòng 73](src/main/java/org/sep490/backend/module/admin/service/impl/AdminDashboardServiceImpl.java#L73)):

```java
@Override
@Cacheable(value = CacheNames.ADMIN_DASHBOARD, key = "'current'", unless = "#result == null")
@Transactional(readOnly = true)
public AdminDashboardResponse getDashboard() { /* giữ nguyên toàn bộ thân hàm */ }
```

Tương tự cho `CuratorDashboardServiceImpl.getDashboard()` với `CacheNames.CURATOR_DASHBOARD`.

**Kiểm tra trước:** `AdminDashboardResponse` và các inner class (`Summary`, `CheckInPoint`, `RevenueSummary`, `UserGrowthPoint`, `RouteEngagementPoint`) đang dùng `@Builder`. Phải có **`@NoArgsConstructor` + `@AllArgsConstructor`** thì Jackson mới deserialize được từ Redis. Mở file DTO kiểm tra, thiếu thì thêm vào.

## Bước 9: Goong Distance Matrix

Tham số là `List<double[]>` — mảng không có `equals/hashCode` hợp lý → **bắt buộc tự sinh key**, không dùng key mặc định. Và **không được cache kết quả haversine fallback** (sẽ đóng băng số liệu kém chính xác 30 ngày).

Vì cần điều khiển "chỉ cache khi Goong thành công", ở đây dùng `RedisTemplate` thủ công thay vì `@Cacheable`:

```java
package org.sep490.backend.module.planner.service.impl;

import org.sep490.backend.config.redis.CacheNames;
import org.springframework.data.redis.core.RedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GoongDistanceServiceImpl implements GoongDistanceService {

    static String VEHICLE = "bike";
    static double FALLBACK_SPEED_MPS = 25_000.0 / 3600.0;

    GoongClient goongClient;
    RedisTemplate<String, Object> redisTemplate;   // <-- thêm dependency

    @Override
    public DistanceMatrixResult getMatrix(List<double[]> points) {
        String cacheKey = buildKey(points);

        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof DistanceMatrixResult hit) {
                return hit;
            }
        } catch (Exception e) {
            log.warn("[Goong] Không đọc được cache, gọi API: {}", e.getMessage());
        }

        DistanceMatrixResult result = callGoong(points);

        // CHỈ cache khi gọi Goong thành công; kết quả haversine không được cache
        if (!result.fallback()) {
            try {
                redisTemplate.opsForValue().set(cacheKey, result, CacheNames.TTL_GOONG);
            } catch (Exception e) {
                log.warn("[Goong] Không ghi được cache: {}", e.getMessage());
            }
        }
        return result;
    }

    /** Toàn bộ logic gọi Goong cũ, đổi tên từ getMatrix thành callGoong. */
    private DistanceMatrixResult callGoong(List<double[]> points) {
        int n = points.size();
        try {
            GoongDistanceMatrixResponse resp = goongClient.distanceMatrix(points, points, VEHICLE);
            if (resp == null || resp.getRows() == null || resp.getRows().size() != n) {
                return haversineMatrix(points);
            }
            double[][] dist = new double[n][n];
            double[][] dur = new double[n][n];
            for (int i = 0; i < n; i++) {
                var elements = resp.getRows().get(i).getElements();
                if (elements == null || elements.size() != n) {
                    return haversineMatrix(points);
                }
                for (int j = 0; j < n; j++) {
                    var el = elements.get(j);
                    if (el == null || el.getDistance() == null || el.getDuration() == null) {
                        return haversineMatrix(points);
                    }
                    dist[i][j] = el.getDistance().getValue();
                    dur[i][j] = el.getDuration().getValue();
                }
            }
            return new DistanceMatrixResult(dist, dur, false);
        } catch (Exception e) {
            log.warn("[Goong] DistanceMatrix lỗi, fallback Haversine: {}", e.getMessage());
            return haversineMatrix(points);
        }
    }

    /**
     * Key = SHA-256 của chuỗi toạ độ làm tròn 5 chữ số (~1m).
     * GIỮ NGUYÊN thứ tự điểm vì ma trận có hướng.
     */
    private String buildKey(List<double[]> points) {
        StringBuilder sb = new StringBuilder(VEHICLE);
        for (double[] p : points) {
            sb.append('|').append(String.format("%.5f,%.5f", p[0], p[1]));
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return "goong:matrix:" + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "goong:matrix:" + sb.toString().hashCode();
        }
    }

    private DistanceMatrixResult haversineMatrix(List<double[]> points) { /* giữ nguyên */ }
}
```

> `DistanceMatrixResult` là một `record` (nằm ở `module/planner/dto/record/`). Record deserialize được với Jackson khi có `jackson-module-parameter-names` — Spring Boot đã kèm sẵn module này. Nếu gặp lỗi deserialize, chuyển sang lưu dạng DTO Lombok có `@NoArgsConstructor`.
>
> Kiểm tra tên accessor của field fallback trong record (`result.fallback()` ở trên) và sửa cho khớp.

## Bước 10: AI rerank + Keycloak admin token

### 10.1 — `AISuggestionServiceImpl`

Chỉ cache tầng `rerankWithLlm` (output thuần của LLM), **không cache** `suggestByDescription` toàn bộ vì hàm ngoài còn gắn rating + trạng thái check-in theo từng user.

Vì `rerankWithLlm` là private → `@Cacheable` không chạy (nguyên tắc #5). Hai cách:
- Đổi nó thành `public` và tách sang một bean riêng (`LlmRerankCache`), hoặc
- Dùng `RedisTemplate` thủ công như Bước 9.

Key: SHA-256 của `description.trim().toLowerCase()` + danh sách hotspot ID **đã sort**.

### 10.2 — `KeyCloakAuthClient.fetchAdminAccessToken()`

Token này là secret → dùng `authRedisTemplate` (DB 1), không đi qua CacheManager JSON:

```java
@Qualifier("authRedisTemplate")
private final StringRedisTemplate authRedis;

private String fetchAdminAccessToken() {
    try {
        String cached = authRedis.opsForValue().get(CacheNames.KEY_KC_ADMIN_TOKEN);
        if (cached != null) {
            return cached;
        }
    } catch (Exception e) {
        log.warn("Không đọc được admin token từ Redis: {}", e.getMessage());
    }

    KeyCloakTokenResponse response = /* logic gọi Keycloak hiện tại */;
    String token = response.getAccessToken();

    try {
        long ttl = Math.max(response.getExpiresIn() - 10, 5);   // trừ 10s an toàn
        authRedis.opsForValue().set(CacheNames.KEY_KC_ADMIN_TOKEN, token, Duration.ofSeconds(ttl));
    } catch (Exception e) {
        log.warn("Không ghi được admin token vào Redis: {}", e.getMessage());
    }
    return token;
}
```

`KeyCloakAuthClient` dùng `@RequiredArgsConstructor` với field `private final` → thêm field mới là xong. Nhớ `@Qualifier("authRedisTemplate")` trên field để chỉ rõ dùng DB 1.

### Kết quả verify bước 8–10 (đã chạy thật ngày 2026-08-04)

| Kiểm tra | Kết quả |
|---|---|
| `/api/admin/dashboard` | 1.147s → 0.088s (**nhanh 13 lần**), TTL 179s |
| `/api/curator/dashboard` | 0.155s → 0.031s, key `curatorDashboard::current` |
| Cache Goong (`DistanceMatrixResult`) | Sửa `EVERYTHING` xong mới serialize được — xem cảnh báo ở 5.2 |
| Keycloak admin token | Ghi `kc:admin-token` vào **DB 1**, TTL 1782s |
| Token được tái dùng | Lần gọi 2 chỉ có `GET`, **không có `SET`** → không gọi lại Keycloak |
| `EVERYTHING` không phá cache cũ | `tags::6`, `adminDashboard`, `curatorDashboard` vẫn chạy đúng |
| **Redis chết → app vẫn 200** | 4 endpoint đều 200, log `CacheErrorHandler` đúng |
| Redis sống lại | App tự kết nối lại |

**Lỗi phát hiện và đã sửa ở bước này:**
1. **`NON_FINAL` làm cache Goong không bao giờ hit** — record không có `@class`. Đổi sang `EVERYTHING` (mục 5.2).
2. **`fetchAdminAccessToken()` chưa dùng cache** — đã inject `authRedis` nhưng thân hàm vẫn gọi Keycloak mới mỗi lần.
3. **AI rerank chưa cache** — đã bổ sung, dùng `RedisTemplate` thủ công vì `rerankWithLlm` là private (`@Cacheable` không chạy với self-invocation).

**Mẹo verify khi endpoint cần auth:** `/api/v1/custom-plans/optimize` và `/suggest-by-description` đều yêu cầu JWT nên không curl trực tiếp được. Cách thay thế: kiểm chứng serialize bằng một class Java nhỏ chạy độc lập với `jackson-databind` từ `~/.m2`, so sánh `NON_FINAL` và `EVERYTHING` trên đúng kiểu dữ liệu thật.

---

# PHASE 3 — RATING SUMMARY + CHECK-IN (bước 11)

## Bước 11: Pattern multi-key (MGET)

`RatingSummaryApplier` nhận **một danh sách ID** và trả map → `@Cacheable` không dùng được. Phải viết cache-aside thủ công với partial-miss.

Thuật toán:
```
1. List<Long> ids → List<String> keys: "rating:hotspot:{id}"
2. MGET tất cả keys một lần
3. Lọc ra các id bị miss
4. Query DB CHỈ cho phần miss
5. Ghi ngược từng key vào Redis, TTL 10 phút
6. Trộn hit + miss rồi trả về
```

Sửa `RatingSummaryApplier` — thêm `RedisTemplate` và một lớp cache dùng chung cho cả 3 loại target:

```java
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RatingSummaryApplier {

    ReviewRepository reviewRepository;
    RedisTemplate<String, Object> redisTemplate;   // thêm

    private static final Duration TTL = Duration.ofMinutes(10);

    /**
     * Cache-aside nhiều key: MGET trước, chỉ query DB phần miss.
     * Redis lỗi thì bỏ qua hoàn toàn, đi thẳng DB.
     */
    private Map<Long, Summary> loadWithCache(String type, List<Long> ids,
                                             Function<List<Long>, Map<Long, Summary>> dbLoader) {
        Map<Long, Summary> result = new HashMap<>();
        List<Long> missIds = new ArrayList<>(ids);

        try {
            List<String> keys = ids.stream().map(id -> CacheNames.KEY_RATING + type + ":" + id).toList();
            List<Object> cached = redisTemplate.opsForValue().multiGet(keys);
            if (cached != null) {
                missIds = new ArrayList<>();
                for (int i = 0; i < ids.size(); i++) {
                    Object value = cached.get(i);
                    if (value instanceof Summary hit) {
                        result.put(ids.get(i), hit);
                    } else {
                        missIds.add(ids.get(i));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Lỗi đọc cache rating, truy vấn toàn bộ từ DB: {}", e.getMessage());
        }

        if (!missIds.isEmpty()) {
            Map<Long, Summary> fromDb = dbLoader.apply(missIds);
            for (Long id : missIds) {
                Summary summary = fromDb.getOrDefault(id, Summary.empty());
                result.put(id, summary);
                try {
                    redisTemplate.opsForValue()
                            .set(CacheNames.KEY_RATING + type + ":" + id, summary, TTL);
                } catch (Exception e) {
                    log.warn("Lỗi ghi cache rating id={}: {}", id, e.getMessage());
                }
            }
        }
        return result;
    }
}
```

Rồi trong `applyToHotspots` / `applyToRoutes` / `applyToStories`, thay `summaryLoader` hiện tại bằng lời gọi `loadWithCache("hotspot", ids, ...)`, `loadWithCache("route", ...)`, `loadWithCache("story", ...)`.

> `Summary` đang là `private record` ở [dòng 109](src/main/java/org/sep490/backend/module/content/service/impl/RatingSummaryApplier.java#L109). Để serialize được, đổi nó thành **package-private hoặc public** (bỏ `private`). Nếu Jackson vẫn không deserialize được record, chuyển sang class Lombok `@Data @NoArgsConstructor @AllArgsConstructor`.

**Invalidation** — trong `ReviewServiceImpl`, mọi thao tác create/update/delete/đổi status review:
```java
redisTemplate.delete(CacheNames.KEY_RATING + targetType + ":" + targetId);
```
Đặt trong `@TransactionalEventListener(phase = AFTER_COMMIT)` để tránh race trước commit (nguyên tắc #6). Dự án đã có sẵn pattern này ở `module/exploration/event/listener/` — làm theo mẫu đó.

`toggleLikeReview` **không** ảnh hưởng rating → không cần evict.

**Check-in status:** `CheckInStatusApplier.apply(...)` → dùng Redis SET `checkin:user:{userId}`, đọc bằng `SMEMBERS` rồi lọc trên bộ nhớ, TTL 1 giờ. Cần thêm query mới vào `UserHotspotProgressRepository`:
```java
/** Toàn bộ hotspot user đã check-in — dùng để nạp cache Redis SET một lần. */
@Query("SELECT p.hotspot.hotspotId FROM UserHotspotProgress p WHERE p.user.userId = :userId")
List<Long> findAllCheckedInHotspotIds(@Param("userId") Long userId);
```
Cache **toàn bộ** hotspot user từng check-in (không chỉ các id đang hỏi) để lần gọi sau với danh sách khác vẫn dùng được. `SADD` thêm khi `UserHotspotProgressServiceImpl.checkIn(...)` chạy xong — nhưng **chỉ khi key đã tồn tại**, chưa có thì để lần đọc sau tự nạp đủ từ DB. Set này chỉ tăng dần (không ai "un-check-in") nên rủi ro lệch rất thấp.

### Kết quả verify bước 11 (đã chạy thật ngày 2026-08-04)

| Kiểm tra | Kết quả |
|---|---|
| `GET /api/v1/hotspots` | 2.11s → 0.15s (**nhanh 14 lần**), 32 key `rating:*` |
| TTL | 597s (~10 phút) |
| Key format | `rating:hotspot:8`, `rating:story:14` — khớp giữa ghi và xoá |
| **Partial-miss** | Xoá 3/32 key → gọi lại chỉ có **đúng 3 `SET`**, 29 key còn lại đọc từ cache |
| Điểm evict | 4 chỗ đúng: create / update / updateStatus / delete |
| **Redis chết → app vẫn 200** | 2.15s (chỉ 1 timeout), request sau 0.097s |
| Mạch tự đóng | Sau 30s Redis sống lại, cache hoạt động bình thường |

**Lỗi nghiêm trọng phát hiện ở bước này:** ghi cache trong vòng lặp làm request mất **88 giây** và trả `HTTP 000` khi Redis chết. Đã sửa bằng `multiSet` + pipeline + `RedisCircuitBreaker` (mục 5.6) → còn **2.15s**.

**Ghi chú thiết kế:** kế hoạch ban đầu đề xuất evict trong `@TransactionalEventListener(AFTER_COMMIT)`. Thực tế dùng lời gọi trực tiếp `evictRatingCache(review)` ngay sau `save()` cho đơn giản. Đánh đổi: có cửa sổ race rất hẹp giữa evict và commit — nếu đúng lúc đó có request khác đọc, nó có thể cache lại giá trị cũ và giữ tới 10 phút. Với đồ án chấp nhận được; muốn chặt chẽ thì chuyển sang `AFTER_COMMIT` theo mẫu ở `module/exploration/event/listener/`.

**Endpoint review cần auth** (`/api/reviews`, `/api/reviews/summary` đều trả 401) nên eviction chưa test được end-to-end. Đã kiểm chứng gián tiếp: 4 điểm gọi đúng chỗ và key sinh ra khớp định dạng key đang lưu.

---

# PHASE 6 — LEADERBOARD + COUNTER USER (bước 12–13)

> Làm Phase 6 **trước** Phase 4 và 5: đây là vùng an toàn (thuần `@Cacheable`), tích luỹ kinh nghiệm trước khi động vào counter và auth.

## Bước 12: Leaderboard

**Quyết định thiết kế — không dùng ZSET.** SQL hiện tại tie-break theo `totalXp DESC, createdAt ASC, userId ASC` ([UserServiceImpl.java:238-241](src/main/java/org/sep490/backend/module/user/service/impl/UserServiceImpl.java#L238-L241)). ZSET chỉ có **một score kiểu `double`** nên không tái tạo được 3 tầng đó → thứ hạng trong `/leaderboard/me` sẽ lệch với danh sách. Cache TTL 60 giây đơn giản hơn nhiều mà vẫn đúng 100%.

`getXpLeaderboard` trả `Page` → cache `List` + `total` rồi dựng lại (nguyên tắc #4). Và `isCurrentUser` phụ thuộc người xem → **phải gán SAU khi lấy từ cache**, không được nằm trong dữ liệu cache.

Tạo DTO trung gian:
```java
package org.sep490.backend.module.user.dto.response;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardPageCache {
    private List<LeaderboardEntryResponse> entries;
    private long totalElements;
}
```

Tách phần cache sang một bean riêng (tránh self-invocation — nguyên tắc #5):
```java
@Service
@RequiredArgsConstructor
public class LeaderboardCacheService {

    private final UserRepository userRepository;

    @Cacheable(value = CacheNames.LEADERBOARD, key = "#page + ':' + #size")
    @Transactional(readOnly = true)
    public LeaderboardPageCache loadPage(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<User> result = userRepository.findLeaderboardByXp(
                UserStatus.ACTIVE, UserRole.EXPLORER, pageable);

        List<LeaderboardEntryResponse> entries = new ArrayList<>(result.getContent().size());
        int offset = page * size;
        for (int i = 0; i < result.getContent().size(); i++) {
            User u = result.getContent().get(i);
            LeaderboardEntryResponse e = new LeaderboardEntryResponse();
            e.setRank(offset + i + 1);
            e.setUserId(u.getUserId());
            e.setUsername(u.getUsername());
            e.setDisplayName(u.getDisplayName());
            e.setAvatarUrl(u.getAvatarUrl());
            e.setTotalXp(u.getTotalXp() != null ? u.getTotalXp() : 0);
            e.setLevelName(u.getLevel() != null ? u.getLevel().getName() : null);
            // KHÔNG set isCurrentUser ở đây — phụ thuộc người xem
            entries.add(e);
        }
        return LeaderboardPageCache.builder()
                .entries(entries)
                .totalElements(result.getTotalElements())
                .build();
    }
}
```

Rồi `UserServiceImpl.getXpLeaderboard` gọi bean này và gán `isCurrentUser` sau:
```java
@Override
@Transactional(readOnly = true)
public Page<LeaderboardEntryResponse> getXpLeaderboard(LeaderboardFilterRequest filter) {
    LeaderboardPageCache cached =
            leaderboardCacheService.loadPage(filter.getPage(), filter.getSize());
    User viewer = findCurrentUserOrNull();

    List<LeaderboardEntryResponse> entries = cached.getEntries().stream()
            .map(e -> {
                e.setIsCurrentUser(viewer == null ? null : viewer.getUserId().equals(e.getUserId()));
                return e;
            })
            .toList();

    return new PageImpl<>(entries,
            PageRequest.of(filter.getPage(), filter.getSize()),
            cached.getTotalElements());
}
```

> ⚠️ Object lấy từ cache Redis là bản deserialize mới mỗi lần nên set field lên nó an toàn. Nhưng nếu sau này bạn đổi sang cache in-memory (Caffeine), việc mutate object dùng chung sẽ gây bug — khi đó phải copy trước khi set.

`getMyXpRank()`: cache theo `userId`, TTL 60s, dùng `CacheNames.MY_RANK`.

## Bước 13: Profile counter, notification badge, getCurrentUser

### 13.1 — Profile counter

`enrichProfileResponse(User)` ([dòng 415](src/main/java/org/sep490/backend/module/user/service/impl/UserServiceImpl.java#L415)) chạy 3 COUNT mỗi user, và bị gọi **cho từng dòng** trong `getAllUsersWithFilter` ([dòng 208](src/main/java/org/sep490/backend/module/user/service/impl/UserServiceImpl.java#L208)) → trang 20 user = **60 query**.

Dùng Redis Hash:
```java
private void applyCounters(UserProfileResponse response, User user) {
    String key = String.format(CacheNames.KEY_USER_COUNTS, user.getUserId());
    try {
        Map<Object, Object> cached = redisTemplate.opsForHash().entries(key);
        if (!cached.isEmpty()) {
            response.setTotalFollowers(Long.parseLong(cached.get("followers").toString()));
            response.setTotalFollowing(Long.parseLong(cached.get("following").toString()));
            response.setTotalPosts(Long.parseLong(cached.get("posts").toString()));
            return;
        }
    } catch (Exception e) {
        log.warn("Lỗi đọc counter user {}: {}", user.getUserId(), e.getMessage());
    }

    long followers = userFollowRepository.countByFollowing(user);
    long following = userFollowRepository.countByFollower(user);
    long posts = postRepository.countByUser(user);

    response.setTotalFollowers(followers);
    response.setTotalFollowing(following);
    response.setTotalPosts(posts);

    try {
        redisTemplate.opsForHash().putAll(key, Map.of(
                "followers", String.valueOf(followers),
                "following", String.valueOf(following),
                "posts", String.valueOf(posts)));
        redisTemplate.expire(key, Duration.ofMinutes(15));
    } catch (Exception e) {
        log.warn("Lỗi ghi counter user {}: {}", user.getUserId(), e.getMessage());
    }
}
```

Evict (`redisTemplate.delete(key)`) tại: `followUser`/`unfollowUser` (**xoá cả 2 phía** — người follow và người được follow), tạo/xoá post.

### 13.2 — Notification badge

`countByUser_UserIdAndIsReadFalse(Long)` bị mobile poll liên tục. `SET notif:unread:{userId} <n>` TTL 5 phút; `DEL` khi `markAsRead` hoặc tạo notification mới.

### 13.3 — `getCurrentUser()`

Gọi ở **mọi** request đã xác thực. **Không cache entity `User`** (nguyên tắc #3 — nó có lazy `level`). Chỉ cache ánh xạ nhẹ `keycloakUserId -> userId`:

```java
// Bean riêng để tránh self-invocation
@Cacheable(value = CacheNames.USER_BY_KEYCLOAK, key = "#keycloakUserId")
public Long resolveUserId(String keycloakUserId) {
    return userRepository.findByKeycloakUserId(keycloakUserId)
            .map(User::getUserId)
            .orElse(null);
}
```
Rồi `getCurrentUser()` dùng `userRepository.findById(id)`. Lợi ích khiêm tốn hơn cache cả entity nhưng an toàn. Evict khi khoá/xoá user (`lockUser`, `unlockUser`).

### Kết quả verify bước 12–13 (đã chạy thật ngày 2026-08-04)

| Kiểm tra | Kết quả |
|---|---|
| `/api/users/leaderboard` | 2.25s → 0.11s (**nhanh 20 lần**), key `leaderboard::0:10`, TTL 57s |
| **`isCurrentUser` KHÔNG bị cache** | Trong Redis là `"isCurrentUser":null` — không rò rỉ giữa các user |
| `/api/admin/users` | 8 key `user:{id}:counts`, 3 field mỗi key, TTL 897s |
| Counter đọc từ cache | Lần 2 chỉ có **8 `HGETALL`**, không `HSET` → thay 24 query COUNT bằng 8 lệnh Redis |
| **Redis chết → app vẫn 200** | 4 endpoint đều 200 (2–4s), ngắt mạch chỉ kích hoạt **1 lần** |
| Redis sống lại | 0.055s, cache ghi bình thường |

**File mới tạo ở bước này:**
- `module/user/dto/response/LeaderboardPageCache.java` — DTO cache (`List` + `totalElements`, không cache `Page`)
- `module/user/service/impl/LeaderboardCacheService.java` — bean riêng để `@Cacheable` đi qua proxy
- `module/user/service/impl/UserIdCacheService.java` — cache `keycloakUserId -> userId`

**Chi tiết dễ bỏ sót:** `getMyXpRank()` không nhận tham số nên `@Cacheable` không tự tạo key được. Phải tách sang method có tham số, và **key phải gồm cả `xp`**:
```java
@Cacheable(value = CacheNames.MY_RANK, key = "#userId + ':' + #xp")
public long countRankedAbove(Long userId, int xp, LocalDateTime createdAt) { ... }
```
Nếu chỉ key theo `userId`, user vừa ăn XP xong vẫn thấy thứ hạng cũ suốt 60 giây. Đưa `xp` vào key thì XP đổi là key đổi, hạng cập nhật ngay.

**Ghi chú về thời gian khi Redis chết:** 2–4s (không phải mức 0.097s như các chỗ dùng `RedisCircuitBreaker`) vì `@Cacheable` đi qua `CacheErrorHandler` chứ không qua circuit breaker. Vẫn chấp nhận được và không có nguy cơ 88s như Bước 11 — mỗi request chỉ gọi cache vài lần, không phải hàng chục.

---

# PHASE 4 — COUNTER MẠNG XÃ HỘI (bước 14)

## Bước 14: Post/Review counters

Đây là điểm nghẽn nặng nhất: [PostMapper.java:34-42](src/main/java/org/sep490/backend/module/social/mapper/PostMapper.java#L34-L42) nạp **toàn bộ** `post_actions` của mỗi post chỉ để đếm. Post 1000 like → load 1000 row cho mỗi post trong trang newsfeed.

> **Khuyến nghị thẳng thắn:** cách đúng nhất về kỹ thuật là thêm cột denormalized `like_count`/`comment_count`/`share_count` vào bảng `posts` (dự án đã có thư mục `migration/` chứa SQL viết tay và `ddl-auto: update`), Redis chỉ làm lớp đọc. Hướng dưới đây dùng Redis theo lựa chọn của bạn, DB vẫn là nguồn sự thật.

### 14.1 — Thêm query đếm vào `PostActionRepository`

```java
long countByPost_PostIdAndActionType(Long postId, PostActionType actionType);

boolean existsByPost_PostIdAndUser_UserIdAndActionType(
        Long postId, Long userId, PostActionType actionType);
```

### 14.2 — Bỏ đếm khỏi `PostMapper`

Sửa [PostMapper.java:34-36](src/main/java/org/sep490/backend/module/social/mapper/PostMapper.java#L34-L36) — thay 3 dòng `@Mapping` count bằng:
```java
@Mapping(target = "likeCount", ignore = true)
@Mapping(target = "commentCount", ignore = true)
@Mapping(target = "shareCount", ignore = true)
```
Và xoá luôn method `countActions(...)` ở [dòng 39-42](src/main/java/org/sep490/backend/module/social/mapper/PostMapper.java#L39-L42).

### 14.3 — Gán counter ở tầng service

`PostServiceImpl.toResponseWithLiked(...)` ([dòng 468](src/main/java/org/sep490/backend/module/social/service/impl/PostServiceImpl.java#L468)):

```java
private PostResponse toResponseWithLiked(Post post, Long currentUserId) {
    PostResponse response = postMapper.toResponse(post);
    applyCounts(response, post.getPostId());
    response.setIsLiked(isLikedBy(post.getPostId(), currentUserId));
    if (response.getSharedPost() != null && post.getSharedPost() != null) {
        applyCounts(response.getSharedPost(), post.getSharedPost().getPostId());
        response.getSharedPost().setIsLiked(
                isLikedBy(post.getSharedPost().getPostId(), currentUserId));
    }
    return response;
}

/**
 * Đọc counter từ Redis Hash; miss thì đếm thật từ DB rồi nạp đầy đủ vào Redis.
 * TUYỆT ĐỐI không HINCRBY lên key đang miss — sẽ tạo counter bắt đầu từ 0 và sai vĩnh viễn.
 */
private void applyCounts(PostResponse response, Long postId) {
    String key = String.format(CacheNames.KEY_POST_COUNTS, postId);
    try {
        Map<Object, Object> cached = redisTemplate.opsForHash().entries(key);
        if (!cached.isEmpty()) {
            response.setLikeCount(Long.parseLong(cached.get("like").toString()));
            response.setCommentCount(Long.parseLong(cached.get("comment").toString()));
            response.setShareCount(Long.parseLong(cached.get("share").toString()));
            return;
        }
    } catch (Exception e) {
        log.warn("Lỗi đọc counter post {}: {}", postId, e.getMessage());
    }

    long like = postActionRepository.countByPost_PostIdAndActionType(postId, PostActionType.LIKE);
    long comment = postActionRepository.countByPost_PostIdAndActionType(postId, PostActionType.COMMENT);
    long share = postActionRepository.countByPost_PostIdAndActionType(postId, PostActionType.SHARE);

    response.setLikeCount(like);
    response.setCommentCount(comment);
    response.setShareCount(share);

    try {
        redisTemplate.opsForHash().putAll(key, Map.of(
                "like", String.valueOf(like),
                "comment", String.valueOf(comment),
                "share", String.valueOf(share)));
        redisTemplate.expire(key, Duration.ofHours(6));
    } catch (Exception e) {
        log.warn("Lỗi ghi counter post {}: {}", postId, e.getMessage());
    }
}

private boolean isLikedBy(Long postId, Long userId) {
    return postActionRepository.existsByPost_PostIdAndUser_UserIdAndActionType(
            postId, userId, PostActionType.LIKE);
}
```

### 14.4 — Invalidation: dùng DEL, không dùng HINCRBY

Trong `toggleLikePost` ([dòng 373](src/main/java/org/sep490/backend/module/social/service/impl/PostServiceImpl.java#L373)), `commentPost` ([dòng 399](src/main/java/org/sep490/backend/module/social/service/impl/PostServiceImpl.java#L399)), `sharePost` ([dòng 426](src/main/java/org/sep490/backend/module/social/service/impl/PostServiceImpl.java#L426)) — sau khi lưu DB:

```java
redisTemplate.delete(String.format(CacheNames.KEY_POST_COUNTS, id));
```

> **Vì sao DEL chứ không HINCRBY?** `HINCRBY` lên một key đang miss sẽ tạo counter bắt đầu từ 0 và **sai vĩnh viễn**. Muốn `HINCRBY` an toàn phải kiểm tra tồn tại + incr trong một Lua script atomic. `DEL` chỉ tốn thêm 1 query ở lần đọc kế tiếp nhưng **không bao giờ lệch số** — với đồ án đây là lựa chọn đúng.

Làm y hệt cho `ReviewMapper` (dòng ~27) + `reviewActions` + `toggleLikeReview`.

### 14.5 — Verify BẮT BUỘC

Like/unlike/comment/share liên tục rồi đối chiếu số hiển thị với:
```sql
SELECT action_type, count(*) FROM post_actions WHERE post_id = ? GROUP BY action_type;
```
Phải khớp tuyệt đối.

### Kết quả verify bước 14 (đã chạy thật ngày 2026-08-04)

Mọi endpoint post đều cần JWT nên **không test được end-to-end bằng curl**. Thay vào đó viết unit test cho `PostCounterCache` — cách này còn kiểm được các trường hợp biên khó dựng qua API.

`src/test/java/.../social/service/impl/PostCounterCacheTest.java` — **6/6 pass**:

| Trường hợp | Kỳ vọng | Kết quả |
|---|---|---|
| Cache có đủ 3 field | Đọc Redis, KHÔNG gọi DB | pass |
| Cache trống | Đếm lại từ DB, ghi **đủ 3 field** (`like=4, comment=1, share=0`) | pass |
| Post chưa có tương tác | Trả 0 cho cả 3, không null | pass |
| **Cache thiếu field** (chỉ 2/3) | Coi như miss, lấy số THẬT từ DB thay vì giá trị hỏng | pass |
| `postId` null | Bỏ qua, không gọi DB | pass |
| `evict(null)` | Bỏ qua | pass |

Trường hợp "cache thiếu field" quan trọng nhất: nếu code chấp nhận cache không đủ 3 field, counter sẽ hiển thị sai vĩnh viễn. Điều kiện `cached.size() == 3` chặn việc này.

Không hồi quy: `/api/v1/hotspots`, `/api/tags/6`, `/api/users/leaderboard`, `/api/admin/users`, `/api/admin/dashboard` đều 200, 43 key trong Redis. Redis chết → cả 4 endpoint vẫn 200 (2–4s), ngắt mạch kích hoạt 1 lần.

**Chi tiết dễ sai:** trong `sharePost`, `shareCount` tăng trên **post GỐC** chứ không phải bài chia sẻ mới tạo — phải `evict(id)` của post gốc.

### Hai lỗi CÓ SẴN đã sửa kèm (không do Redis)

**1. `/api/posts`, `/api/posts/{id}` trả 500 cho khách vãng lai.**
Cả hai gọi `userService.getCurrentUser()` trong khi endpoint được khai là public.

> ⚠️ **Cách sửa "hiển nhiên" là SAI.** Bọc `try/catch` quanh `getCurrentUser()` **không hoạt động**:
> ```java
> // SAI — vẫn nổ UnexpectedRollbackException
> try { return userService.getCurrentUser().getUserId(); }
> catch (RuntimeException e) { return null; }
> ```
> `getCurrentUser()` có `@Transactional`. Khi nó ném lỗi bên trong transaction cha, transaction **đã bị đánh dấu rollback-only**. Bắt được exception cũng vô ích — lúc commit vẫn nổ:
> ```
> UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only
> ```
> Đúng: kiểm tra token **TRƯỚC**, chỉ gọi khi chắc chắn có người dùng:
> ```java
> private Long findCurrentUserIdOrNull() {
>     if (SecurityUtils.getCurrentUserKeyCloakId().isEmpty()) {
>         return null;
>     }
>     return userService.getCurrentUser().getUserId();
> }
> ```
> `ReviewServiceImpl.getCurrentUserIdOrNull()` mắc đúng lỗi này — đã sửa cùng.

**2. `Post` có 3 quan hệ `@ManyToMany` bị EAGER** → `GET /api/posts?status=PENDING` chậm tới mức timeout.

`@ManyToMany` **mặc định là EAGER** (khác `@OneToMany`). Trong `Post.java`, `taggedHotspots`, `taggedRoutes`, `tags` đều không khai `fetch` → mỗi post nạp về đều kéo theo 3 bảng JOIN, kể cả khi response không dùng.

```java
@ManyToMany(fetch = FetchType.LAZY)   // phải khai tường minh
```

`default_batch_fetch_size: 100` sẵn có trong `application.yml` sẽ gom các lazy này theo lô.

**Đo sau khi sửa:**

| Endpoint | Trước | Sau (lần 1) | Sau (lần 2, cache ấm) |
|---|---|---|---|
| `/api/posts` | HTTP 500 | 2.59s | **0.55s** |
| `/api/posts?status=PENDING` | timeout | **0.43s** | 0.43s |
| `/api/posts/{id}` | HTTP 500 | 0.80s | — |

> **Lưu ý quan trọng khi chẩn đoán:** Redis **KHÔNG cache danh sách bài viết**, chỉ cache counter (`post:{id}:counts`). Nên nếu API post chậm, nguyên nhân nằm ở **query DB / fetch type**, không phải "Redis chưa apply". Kiểm tra bằng `spring.jpa.show-sql=true` rồi đếm số dòng `Hibernate:`.

### Phạm vi cache của bước 14 — endpoint nào đã được apply

Tất cả endpoint đi qua `PostServiceImpl.toResponseWithLiked(...)` đều dùng counter cache:

| Endpoint | Đã apply? | Ghi chú |
|---|---|---|
| `GET /api/posts` | có | public |
| `GET /api/posts/{id}` | có | public |
| **`GET /api/posts/newsfeed`** | **có** | cần auth |
| `GET /api/posts/my-posts` | có | cần auth |
| `GET /api/posts/hotspot/{id}` | có | cần auth |
| `POST /api/posts/{id}/like` \| `/comment` \| `/share` | có | evict rồi trả response mới |
| `GET /api/posts/{id}/comments` | không | trả `CommentResponse`, không có counter |

Newsfeed dùng chung `toResponseWithLiked` nên tự động được hưởng. Nhưng lưu ý: **cache counter không giúp gì cho câu query newsfeed** — query đó có subquery follow-graph tương quan trong `ORDER BY`, vốn là vấn đề riêng (xem Bước 18.4, cố ý không xử lý trong đợt này).

---

## ⚠️ LỖ HỔNG BẢO MẬT PHÁT HIỆN — CHƯA SỬA

Ghi nhận ngày 2026-08-05 khi verify newsfeed. **Không liên quan Redis**, người dùng chọn xử lý sau.

`SecurityConfig.PUBLIC_ENDPOINTS` dòng 55 có `"/api/posts/**"` — pattern này mở **TOÀN BỘ** endpoint dưới `/api/posts` cho người chưa đăng nhập, bao gồm:

```
POST   /api/posts                    tạo bài viết
PUT    /api/posts/{id}               sửa bài viết
DELETE /api/posts/{id}               xoá bài viết
DELETE /api/posts/{id}/permanent     XOÁ VĨNH VIỄN
POST   /api/posts/{id}/like          like
POST   /api/posts/{id}/comment       bình luận
POST   /api/posts/{id}/share         chia sẻ
GET    /api/posts/newsfeed           newsfeed
GET    /api/posts/my-posts           bài viết của tôi
```

Bất kỳ ai cũng gọi được `DELETE /api/posts/{id}/permanent` mà không cần token.

**Triệu chứng dễ thấy:** `GET /api/posts/newsfeed` không token trả **HTTP 500** (`RuntimeException: Không tìm thấy thông tin người dùng hiện tại`) thay vì **401**. Request lọt qua filter chain rồi mới nổ ở tầng service.

**Cách sửa đề xuất** — thay `"/api/posts/**"` bằng danh sách cụ thể:
```java
// Bỏ "/api/posts/**" khỏi PUBLIC_ENDPOINTS, thay bằng:
.requestMatchers(HttpMethod.GET, "/api/posts").permitAll()
.requestMatchers(HttpMethod.GET, "/api/posts/{id}").permitAll()
.requestMatchers(HttpMethod.GET, "/api/posts/{id}/comments").permitAll()
// mọi thứ khác dưới /api/posts rơi vào anyRequest().authenticated()
```

Kiểm tra tương tự cho `"/api/v1/hotspots/**"`, `"/api/v1/stories/**"`, `"/api/v1/routes/**"`, `"/api/tags/**"` — cùng dạng pattern `/**` nên nhiều khả năng cũng đang mở cả thao tác ghi. Lưu ý `"/api/admin/**"` và `"/api/curator/**"` đang `permitAll()` ở dòng 72–73, chỉ dựa vào `@PreAuthorize` ở tầng method.

**Ngoài phạm vi kế hoạch, đã làm thêm:** `ReviewServiceImpl.search()` trước đây gọi `toResponse` cho từng dòng (2 query mỗi review). Đã đổi sang `applyLikeInfo(...)` gán `likeCount` + `isLiked` cho **cả trang bằng 2 query**, cần thêm `countActionsByReviewIds` và `findLikedReviewIds` vào `ReviewActionRepository`.

---

# PHASE 5 — AUTH (bước 15–17)

## Bước 15: Rate limit resend OTP (làm trước, dễ và an toàn nhất)

[AuthServiceImpl.java:137-146](src/main/java/org/sep490/backend/module/authentication/service/impl/AuthServiceImpl.java#L137-L146) đang tính cooldown 30s bằng cách đọc `createdAt` từ DB. Thay bằng Redis:

```java
@Qualifier("authRedisTemplate")
private final StringRedisTemplate authRedis;

@Override
@Transactional
public void resendOtp(SendOtpRequest request) {
    String email = request.getEmail().trim();
    String cooldownKey = CacheNames.KEY_OTP_COOLDOWN + email.toLowerCase();

    Boolean acquired = authRedis.opsForValue()
            .setIfAbsent(cooldownKey, "1", Duration.ofSeconds(30));

    if (Boolean.FALSE.equals(acquired)) {
        Long ttl = authRedis.getExpire(cooldownKey, TimeUnit.SECONDS);
        long secondsLeft = ttl != null && ttl > 0 ? ttl : 30;
        throw new BusinessException(
                "Vui lòng đợi thêm " + secondsLeft + " giây nữa để yêu cầu gửi lại mã OTP.");
    }

    sendVerificationOtp(email);
}
```

`SET ... NX EX 30` là thao tác atomic — đúng ngữ nghĩa hơn hẳn việc đọc `createdAt`.

**Thêm giới hạn số lần đoán OTP** — hiện tại `verifyEmailWithOtp` ([dòng 109](src/main/java/org/sep490/backend/module/authentication/service/impl/AuthServiceImpl.java#L109)) **hoàn toàn không giới hạn số lần thử**, đây là lỗ hổng thật:

```java
String attemptKey = CacheNames.KEY_OTP_ATTEMPT + email.toLowerCase();
Long attempts = authRedis.opsForValue().increment(attemptKey);
if (attempts != null && attempts == 1L) {
    authRedis.expire(attemptKey, Duration.ofMinutes(15));
}
if (attempts != null && attempts > 5) {
    throw new BusinessException("Bạn đã nhập sai quá nhiều lần. Vui lòng thử lại sau 15 phút.");
}
// ... logic verify hiện tại ...
// Verify thành công thì xoá bộ đếm:
authRedis.delete(attemptKey);
```

## Bước 16: Chuyển OTP + reset token sang Redis

**Quyết định:** chuyển hẳn sang Redis (DB 1, có AOF). OTP sống 5 phút, reset token 15 phút — mất do Redis restart chỉ là phiền toái nhỏ (user bấm gửi lại), đổi lại xoá được 2 bảng rác vô hạn và toàn bộ logic `isExpired()` thủ công.

> Đây là **ngoại lệ có chủ ý** với nguyên tắc #1. Ghi rõ vào `docs/redis.md`.

```java
// Gửi OTP
authRedis.opsForValue().set(CacheNames.KEY_OTP + email.toLowerCase(), otpCode, Duration.ofMinutes(5));

// Verify
String stored = authRedis.opsForValue().get(CacheNames.KEY_OTP + email.toLowerCase());
if (stored == null) {
    throw new BusinessException("Mã OTP đã hết hiệu lực. Vui lòng thử lại sau");
}
if (!stored.equals(userOtp)) {
    throw new BusinessException("Mã OTP không chính xác");
}
authRedis.delete(CacheNames.KEY_OTP + email.toLowerCase());   // one-time use
```

Reset token ([forgotPassword dòng 216](src/main/java/org/sep490/backend/module/authentication/service/impl/AuthServiceImpl.java#L216)) — thay `tokenRepository.deleteByUser(user)` bằng cách lưu ngược để xoá token cũ:

```java
String oldToken = authRedis.opsForValue().get(CacheNames.KEY_PWRESET_USER + user.getUserId());
if (oldToken != null) {
    authRedis.delete(CacheNames.KEY_PWRESET + oldToken);
}
String token = UUID.randomUUID().toString();
authRedis.opsForValue().set(CacheNames.KEY_PWRESET + token,
        String.valueOf(user.getUserId()), Duration.ofMinutes(15));
authRedis.opsForValue().set(CacheNames.KEY_PWRESET_USER + user.getUserId(),
        token, Duration.ofMinutes(15));
```

Sau khi xong: xoá `EmailOtp.java`, `PasswordResetToken.java`, `EmailOtpRepository.java`, `PasswordResetTokenRepository.java` và bỏ chúng khỏi `AuthServiceImpl`. Hai bảng cũ trong DB **không tự bị drop** (`ddl-auto: update` không xoá bảng) — drop tay bằng SQL trong `migration/` nếu muốn.

## Bước 17: JWT denylist khi logout (rủi ro cao nhất — làm cuối)

Hiện `logout(String refreshToken)` ([dòng 210](src/main/java/org/sep490/backend/module/authentication/service/impl/AuthServiceImpl.java#L210)) chỉ gọi Keycloak; **access token đã cấp vẫn hợp lệ tới khi hết hạn tự nhiên**. Đây là lỗ hổng bảo mật thật.

```java
@Override
public void logout(String refreshToken) {
    keyCloakAuthClient.logout(refreshToken);

    // Chặn access token hiện tại tới khi nó hết hạn tự nhiên
    SecurityUtils.getCurrentJwt().ifPresent(jwt -> {
        String jti = jwt.getId();
        Instant expiresAt = jwt.getExpiresAt();
        if (jti != null && expiresAt != null) {
            long ttl = Duration.between(Instant.now(), expiresAt).getSeconds();
            if (ttl > 0) {
                try {
                    authRedis.opsForValue().set(
                            CacheNames.KEY_DENYLIST + jti, "1", Duration.ofSeconds(ttl));
                } catch (Exception e) {
                    log.warn("Không ghi được denylist cho jti {}: {}", jti, e.getMessage());
                }
            }
        }
    });
}
```

Kiểm tra `SecurityUtils` xem đã có hàm lấy `Jwt` chưa; chưa có thì thêm (lấy từ `SecurityContextHolder.getContext().getAuthentication().getPrincipal()`).

Rồi thêm filter kiểm tra trong [SecurityConfig.java](src/main/java/org/sep490/backend/config/security/SecurityConfig.java):

```java
/**
 * FAIL-OPEN có chủ ý: Redis chết thì cho request đi qua, không chặn toàn bộ user.
 * Nhất quán với nguyên tắc "Redis chết → app vẫn chạy".
 */
public class JwtDenylistFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt && jwt.getId() != null) {
            try {
                if (Boolean.TRUE.equals(
                        authRedis.hasKey(CacheNames.KEY_DENYLIST + jwt.getId()))) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            "{\"message\":\"Phiên đăng nhập đã kết thúc. Vui lòng đăng nhập lại\"}");
                    return;
                }
            } catch (Exception e) {
                log.warn("Không kiểm tra được denylist, cho request đi qua: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
```

Đăng ký sau `BearerTokenAuthenticationFilter`:
```java
.addFilterAfter(jwtDenylistFilter, BearerTokenAuthenticationFilter.class)
```

> **Đánh đổi phải cân nhắc:** việc này thêm **một round-trip Redis vào mọi request đã xác thực** (~0.2ms). Với đồ án một instance là chấp nhận được, và nó vá một lỗ hổng thật. Nhưng bắt buộc fail-open như code trên.

**Verify:** login → gọi API bằng access token (OK) → logout → gọi lại **cùng token đó** → phải trả 401.

---

# ĐO HIỆU NĂNG VỚI 1.22 TRIỆU BÀI VIẾT (2026-08-05)

Sau khi chuyển toàn bộ 1.220.055 bài từ PENDING sang APPROVED (`UPDATE` mất 91 giây), đo lại newsfeed.

## Kết luận: Redis KHÔNG cứu được newsfeed ở quy mô này

`EXPLAIN ANALYZE` câu `findNewsfeed` trên 1.22 triệu dòng:

```
Parallel Seq Scan on posts p  (actual time=276..766 rows=406685 loops=3)
  Filter: ((status)::text = 'APPROVED'::text)
Execution Time: 1062 ms
```

**Quét toàn bộ bảng** dù chỉ lấy 10 bài. Chi phí theo trang:

| OFFSET | Execution Time |
|---|---|
| 0 | 1510 ms |
| 10.000 | **7743 ms** |
| 100.000 | 2693 ms |

Lý do Redis không giúp:
1. Redis **chỉ cache counter** (`post:{id}:counts`), không cache danh sách
2. `ORDER BY CASE WHEN ... THEN 0 ELSE 1 END` — biểu thức, không phải cột → **index không dùng được**
3. Sort 1.22 triệu dòng trong RAM mỗi lần gọi

## Nguyên nhân gốc: index có sẵn nhưng query không khớp

`Post.java` đã khai `@Index(name = "idx_post_feed_flow", columnList = "status, visibility, created_at")`.
Nhưng `findNewsfeed` và `findByStatusOptional` **chỉ lọc `status`**, bỏ qua `visibility` → Postgres không dùng được index.

Đo trực tiếp trên DB thật:

| Query | Kế hoạch | Thời gian |
|---|---|---|
| `WHERE status='APPROVED' ORDER BY created_at DESC` | **Seq Scan** | 1348 ms |
| `WHERE status='APPROVED' AND visibility='PUBLIC' ORDER BY created_at DESC` | **Index Scan** | **2.38 ms** |

**Nhanh hơn 567 lần** chỉ nhờ thêm một điều kiện để khớp đủ index. (Phân bố thực tế: 1.220.056 PUBLIC / 1 PRIVATE — thêm điều kiện này gần như không đổi kết quả trả về.)

## Ba cải tiến ĐÃ LÀM và kết quả đo

**1. Thêm `visibility` vào WHERE** để khớp index — hiệu quả lớn nhất, sửa nhỏ nhất:
```java
@Query("SELECT p FROM Post p WHERE p.status = :status AND p.visibility = :visibility " +
       "ORDER BY p.createdAt DESC")
Slice<Post> findByStatusAndVisibility(...);
```
`PostServiceImpl.getPosts` dùng query này khi có `status` cụ thể.

**2. Bỏ `CASE WHEN` khỏi `ORDER BY`** — tách `getNewsfeed` thành 2 query rồi ghép ở service:
```java
List<Long> followingIds = postRepository.findFollowingIds(currentUser.getUserId());
// Phần 1: bài của người đang theo dõi (ưu tiên trước)
merged.addAll(postRepository.findFeedByAuthors(APPROVED, PUBLIC, followingIds, null, limit));
// Phần 2: bù cho đủ bằng bài của những người còn lại
merged.addAll(postRepository.findFeedExcludingAuthors(APPROVED, PUBLIC, excluded, null, remaining));
```
Cả hai query đều dùng được index. `findNewsfeed` cũ giữ lại và đánh `@Deprecated`.

> Bẫy: `NOT IN` với danh sách **rỗng** là lỗi cú pháp SQL. Khi user chưa follow ai, phải truyền một id không tồn tại (`List.of(-1L)`).

**3. Keyset pagination** — thêm tham số `cursor`:
```java
"AND (:cursor IS NULL OR p.createdAt < :cursor) ORDER BY p.createdAt DESC"
```
Hạ tầng đã sẵn sàng; client truyền `createdAt` của bài cuối trang trước thay vì tăng `page`. Hiện `getNewsfeed` vẫn nhận `page/size` để không phá client, nhưng repository đã hỗ trợ keyset khi cần chuyển.

### Kết quả đo (1.22 triệu bài, 2026-08-05)

**Query newsfeed ở độ sâu 10.000 dòng:**

| | Kế hoạch | Thời gian |
|---|---|---|
| Trước (`ORDER BY CASE WHEN`) | **Parallel Seq Scan** | 2052 ms |
| Sau (2 query tách rời) | **Index Scan Backward** | **7.15 ms** |

**Nhanh hơn 287 lần.**

**Qua API `/api/posts?status=APPROVED`:**

| Trang | Trước | Sau |
|---|---|---|
| page=0 (lần 1, cache lạnh) | 3.87 s | 1.96 s |
| page=0 (lần 2, cache ấm) | 0.65 s | **0.10 s** |
| page=100 | — | 0.32 s |
| page=1000 | ~7.7 s | **0.34 s** |

> **Lưu ý:** phần lớn cải thiện đến từ **index**, không phải Redis. Redis chỉ giúp chặng cuối (0.10s so với 1.96s ở lần đầu) nhờ cache counter. Nếu query còn Seq Scan thì cache không cứu được.

> **Bài học chung:** khi API chậm, đừng mặc định là "cache chưa apply". Chạy `EXPLAIN ANALYZE` trước — nếu thấy `Seq Scan` trên bảng lớn thì vấn đề là index/query, không có lớp cache nào cứu được một cách bền vững.

---

# Kết quả verify bước 15–17 (2026-08-05)

Toàn bộ OTP / reset token / denylist đã chuyển sang Redis DB 1 qua một component mới:
`module/authentication/service/impl/AuthTokenStore.java`.

## Đo thực tế qua API

| Kiểm tra | Kết quả |
|---|---|
| Đăng ký → OTP nằm trên Redis DB 1 | key `otp:{email}`, TTL 114s (~2 phút) |
| Bảng `email_otps` | không còn được ghi (entity đã xoá) |
| Cooldown resend (`SET NX EX 30`) | lần 2 bị chặn: *"Vui lòng đợi thêm 26 giây nữa…"* |
| **Chặn vét cạn OTP** | 5 lần sai đầu báo sai mã, **lần 6 trở đi bị chặn** |
| Verify OTP đúng | kích hoạt tài khoản, xoá cả `otp:` lẫn `otp:attempt:` |
| forgot-password | key `pwreset:{token}` TTL 894s + ánh xạ ngược `pwreset:user:{id}` |
| reset-password | đổi mật khẩu OK, token bị xoá ngay |
| **Dùng lại token đã xài** | bị từ chối (one-time use) |

## Test tự động

- `AuthServiceImplTest` — **65/65 pass** (viết lại theo API mới, thêm ca chặn vét cạn)
- `JwtDenylistFilterTest` — **5/5 pass**, gồm ca **fail-open** khi Redis lỗi
- Toàn bộ dự án: **225 test, 1 fail** — fail đó là lỗi CÓ SẴN (`SaveRouteServiceImplTest` kỳ vọng *"Route not found"* nhưng code trả tiếng Việt, có từ commit `96c992a`)

## Lỗ hổng bảo mật đã vá

**1. Không giới hạn số lần đoán OTP.** OTP chỉ 6 chữ số (10^6 tổ hợp) mà trước đây `verifyEmailWithOtp` **không có bộ đếm nào**. Đã thêm `INCR` + `EXPIRE 15m`, chặn sau 5 lần.

**2. `Random` thay vì `SecureRandom` khi sinh OTP.** `java.util.Random` dùng LCG, đoán được state từ vài giá trị đầu ra. OTP là thông tin xác thực nên phải dùng `SecureRandom`.

**3. Logout không chấm dứt phiên.** Keycloak thu hồi được refresh token nhưng **không** thu hồi access token đã cấp. Đã thêm denylist theo `jti` với TTL = thời gian sống còn lại của token, kèm `JwtDenylistFilter` đặt sau `BearerTokenAuthenticationFilter`.

## ⚠️ Lỗi nghiêm trọng phát hiện khi verify: timeout 60 giây

Test tắt Redis cho kết quả **`HTTP 000` sau 60 giây** ở `/api/auth/resend-otp`:
```
Caused by: io.lettuce.core.RedisCommandTimeoutException: Command timed out after 1 minute(s)
```

Nguyên nhân: `AuthRedisConfig` tạo `LettuceConnectionFactory` **thủ công** nên **KHÔNG kế thừa** `spring.data.redis.timeout: 2s` — nó dùng mặc định **60 giây** của Lettuce.

> Đây là bẫy chung cho MỌI `ConnectionFactory` tạo tay. Phải truyền timeout tường minh:
> ```java
> LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
>         .commandTimeout(properties.getTimeout())
>         .clientOptions(ClientOptions.builder()
>                 .socketOptions(SocketOptions.builder()
>                         .connectTimeout(properties.getConnectTimeout())
>                         .build())
>                 .build())
>         .build();
> return new LettuceConnectionFactory(config, clientConfig);
> ```

Đồng thời bọc toàn bộ `AuthTokenStore` bằng `RedisCircuitBreaker` (mục 5.6) với **fail-open**:

| Thao tác | Redis lỗi thì | Lý do |
|---|---|---|
| `acquireResendSlot` | trả 0 (cho gửi) | mất cooldown còn hơn chặn người dùng xác thực email |
| `isAttemptExceeded` | trả false (không chặn) | như trên |
| `isTokenDenied` | trả false (cho qua) | không được chặn toàn bộ người dùng |

**Kết quả sau khi sửa:** `HTTP 000 sau 60s` → **`HTTP 200 sau 7.18s`** (phần lớn 7s là gửi email SMTP, không phải Redis). Ngắt mạch chỉ kích hoạt 1 lần.

## Hai bẫy khi chạy test

**1. Stale `.class` gây `TestEngine failed to discover tests`.** Sau khi xoá entity/đổi chữ ký, `target/test-classes` giữ file `.class` cũ và surefire fail với thông báo hoàn toàn không liên quan. **Luôn `mvnw clean test`**, đừng chỉ `mvnw test`.

**2. Mock trả `null` cho method vốn trả về đối số.** `RatingSummaryApplier.applyToStory(...)` và `CheckInStatusApplier.apply(...)` trả lại chính response. Mock mặc định trả `null` → `assertNotNull` fail. Phải stub:
```java
when(ratingSummaryApplier.applyToStory(any(StoryResponse.class)))
        .thenAnswer(inv -> inv.getArgument(0));
```

## File thay đổi

**Tạo mới:** `AuthTokenStore.java`, `config/security/JwtDenylistFilter.java`, `JwtDenylistFilterTest.java`

**Xoá:** `entity/EmailOtp.java`, `entity/PasswordResetToken.java`, `repository/EmailOtpRepository.java`, `repository/PasswordResetTokenRepository.java`

> Hai bảng `email_otps` và `password_reset_tokens` **không tự bị drop** (`ddl-auto: update` không xoá bảng). Drop tay bằng SQL trong `migration/` nếu muốn dọn sạch.

**Sửa:** `AuthServiceImpl.java`, `SecurityConfig.java`, `AuthRedisConfig.java`, `AuthServiceImplTest.java`, và thêm mock còn thiếu cho `HotspotServiceImplTest`, `RouteServiceImplTest`, `StoryServiceImplTest`, `UserHotspotProgressServiceImplTest`, `UserServiceImplTest`.

## Chưa kiểm chứng được end-to-end

Luồng **login → logout → gọi lại token cũ → 401** chưa test thật vì tài khoản đăng ký qua API bị Keycloak trả **403 "Bạn không có quyền truy cập"** — role EXPLORER không được gán bên Keycloak dù DB đã ghi đúng. Đây là **lỗi có sẵn của luồng đăng ký**, không liên quan bước 15–17. Đã bù bằng `JwtDenylistFilterTest` (5 ca, gồm fail-open).

---

# PHASE 7 — CONTENT + GEO (bước 18)

## Bước 18: Geo và content detail

### 18.1 — `isLocationInVietnam`
`ST_Within` trên polygon biên giới **không bao giờ đổi**. Key = lat/lon làm tròn **3 chữ số** (~110m), TTL 30 ngày. Sai số ~110m ở sát biên giới hoàn toàn ổn cho mục đích validate.

Method này bị **trùng lặp** ở cả `HotspotRepository` và `PartnerInfoRepository` — cache ở tầng service của cả hai chỗ gọi.

### 18.2 — `findNearbyHotspotsWithStatus`
Bị gọi **trong vòng lặp theo từng anchor** ở `AISuggestionServiceImpl.suggestNearby`. Cache theo key làm tròn 3 chữ số + radius + status, TTL 30 phút. **Chỉ cache danh sách ID**, không cache entity (nguyên tắc #3); rating/check-in gắn sau.

### 18.3 — Content detail
`HotspotServiceImpl.getDetail(Long)`, `RouteServiceImpl.getDetail(Long)`, `StoryServiceImpl.getDetail(Long)` — TTL 15 phút, evict khi curator sửa nội dung.

⚠️ Response detail chứa rating + trạng thái check-in **theo từng user** → cache phần "thân" nội dung, gắn rating/check-in **sau** khi lấy từ cache (giống cách xử lý `isCurrentUser` ở Bước 12).

### 18.4 — Không làm trong đợt này
- **`HotspotServiceImpl.getAll()`** đang `findAll()` **không phân trang** + N+1 story. Cache TTL 10 phút che được triệu chứng nhưng **không chữa bệnh** — nên phân trang thật ở lần refactor sau.
- **Newsfeed** (`PostServiceImpl.getNewsfeed`): đã xử lý ở phần tối ưu index, không cần precomputed feed.

### Kết quả verify bước 18 (2026-08-05)

Tạo `module/content/service/impl/GeoQueryCache.java` — cache hai truy vấn PostGIS đắt.

**Đo chi phí query TRƯỚC khi code** (để không cache nhầm chỗ không cần):

| Query | Thời gian | Ghi chú |
|---|---|---|
| `isLocationInVietnam` (ST_Within) | **24 ms** | bảng `country_boundaries` chỉ có **1 dòng** |
| `findNearbyHotspotsWithStatus` (ST_DWithin) | **118 ms** | bảng `hotspots` chỉ có **14 dòng** |

Bảng rất nhỏ nhưng vẫn chậm — PostGIS phải xử lý polygon biên giới phức tạp. Cache có giá trị thật.

**Kết quả qua API `/api/v1/hotspots/nearby`:**

| Lần gọi | Thời gian |
|---|---|
| 1 (cache lạnh) | 2.12 s |
| 2 | 0.053 s |
| 3 | **0.023 s** (nhanh **94 lần**) |

**Kiểm chứng quan trọng nhất — làm tròn toạ độ:**

Gọi 3 toạ độ khác nhau, lệch nhau dưới 110m:
```
lat=21.0281  lon=105.8541   -> 0.027s
lat=21.02849 lon=105.85449  -> 0.039s
lat=21.0275  lon=105.8536   -> 0.023s
```
Kết quả: **chỉ sinh 1 key** `geo:nearby:21.028:105.854:3000:PUBLISHED`, cả 3 đều cache hit.

> Nếu **không làm tròn**, mỗi lời gọi sinh một key mới → cache không bao giờ hit, mà vẫn tốn thêm một round-trip Redis. Đây là lỗi rất dễ mắc và rất khó thấy vì API vẫn chạy đúng.

**Test tự động:** `GeoQueryCacheTest` — **9/9 pass**

| Ca kiểm thử | Ý nghĩa |
|---|---|
| Cache miss → query DB, ghi cache TTL dài | luồng cơ bản |
| Cache hit → KHÔNG chạy ST_Within | xác nhận tiết kiệm thật |
| **Toạ độ lệch <110m dùng chung key** | xác nhận làm tròn hoạt động |
| Toạ độ null → false, không gọi DB | biên |
| Cache lưu **ID**, không lưu entity | tránh lazy association |
| **Hotspot bị xoá → bỏ cache, query lại** | tránh trả dữ liệu ma |
| **Giữ đúng thứ tự gần → xa** | `findAllById` KHÔNG đảm bảo thứ tự |
| Radius khác → key khác | tránh trả sai bán kính |

**Redis chết:** vẫn HTTP 200 (2.05s → 0.04s), ngắt mạch kích hoạt 1 lần.

**Toàn bộ dự án:** 234 test, 1 fail — là lỗi CÓ SẴN `SaveRouteServiceImplTest` (kỳ vọng *"Route not found"*, code trả tiếng Việt từ commit `96c992a`).

### Hai quyết định thiết kế đáng chú ý

**1. Cache danh sách ID, không cache entity `Hotspot`.** Đúng nguyên tắc #3. Khi hit thì `findAllById` — tra theo khoá chính, rất nhanh. Nếu số bản ghi tìm được **ít hơn** số ID trong cache (có hotspot đã bị xoá), bỏ cache và chạy lại PostGIS.

**2. `findAllById` không giữ thứ tự.** Nearby cần đúng thứ tự gần → xa, nên phải sắp lại theo danh sách ID đã cache. Bug này sẽ không lộ ra ở bảng nhỏ nhưng sẽ sai khi dữ liệu lớn.

### Chưa kiểm chứng được

Eviction (`evictNearby`) chưa test qua API vì `PUT /api/v1/hotspots/{id}` trả 400 (thiếu field bắt buộc của multipart form). Đã xác nhận gián tiếp: 3 điểm gọi đúng chỗ (`create`, `update`, `delete`) và key pattern khớp.

---

# Bước 19: `docs/redis.md`

Tạo file tài liệu tiếng Việt gồm:

1. **Redis là gì, vì sao dự án cần** — tóm tắt phần Context.
2. **Các kiểu dữ liệu và khi nào dùng** — bảng ánh xạ vào use case THẬT:

| Kiểu | Dùng khi | Trong dự án này |
|---|---|---|
| String | 1 key = 1 giá trị/JSON; counter đơn | OTP, reset token, denylist, cache DTO |
| Hash | Nhóm nhiều field liên quan, cập nhật lẻ | `post:{id}:counts`, `user:{id}:counts` |
| List | Hàng đợi FIFO/LIFO, log gần đây | chưa dùng |
| Set | Kiểm tra thành viên, khử trùng lặp | `post:{id}:likers`, `checkin:user:{id}` |
| Sorted Set | Xếp hạng, top-N | leaderboard — **đã cân nhắc và KHÔNG dùng**, xem Bước 12 |
| Bitmap | Cờ nhị phân theo user/ngày | ý tưởng cho streak check-in |
| HyperLogLog | Đếm xấp xỉ unique, ~12KB | đếm unique view hotspot |
| Geo | Truy vấn bán kính | **không cần** — đã có PostGIS |
| Stream | Event log bền, consumer group | so sánh với `ApplicationEventPublisher` hiện có |

3. **Chế độ triển khai:** Standalone / Sentinel / Cluster — kết luận: đồ án này chỉ cần **Standalone**; Sentinel khi cần HA; Cluster khi dữ liệu vượt RAM một máy.
4. **Persistence:** RDB vs AOF — vì sao chọn `appendonly yes`.
5. **Eviction policy:** giải thích `allkeys-lru` và rủi ro với DB 1.
6. **Pattern cache:** cache-aside (đang dùng), write-through, TTL + jitter, **cache stampede**.
7. **7 nguyên tắc** ở đầu tài liệu này.
8. **Ma trận invalidation** (bảng dưới).
9. **Cẩm nang vận hành:** lệnh `redis-cli` hay dùng.

---

# Ma trận invalidation

| Key pattern | DB | TTL | Ghi bởi | Xoá bởi |
|---|---|---|---|---|
| `levels::*` | 0 | 6h | `getAllLevels` | CRUD level |
| `tags::*` | 0 | 1h | `TagServiceImpl` | CRUD tag / route-tag / story-tag |
| `subscriptionPlans::*` | 0 | 6h | `getActivePlanByType` | CRUD plan, plan rule |
| `adminDashboard::*`, `curatorDashboard::*` | 0 | 3m | dashboard service | chỉ TTL |
| `goong:matrix:{hash}` | 0 | 30d | `GoongDistanceServiceImpl` | không bao giờ |
| `aiRerank::{hash}` | 0 | 24h | `AISuggestionServiceImpl` | không bao giờ |
| `kc:admin-token` | 1 | expires_in − 10s | `KeyCloakAuthClient` | chỉ TTL |
| `rating:{type}:{id}` | 0 | 10m | `RatingSummaryApplier` | review create/update/delete/status (AFTER_COMMIT) |
| `checkin:user:{userId}` | 0 | 1h | `CheckInStatusApplier` | `checkIn(...)` (SADD thêm) |
| `post:{id}:counts` | 0 | 6h | `PostServiceImpl` | toggleLike / comment / share (**DEL**) |
| `otp:{email}` | 1 | 5m | `sendVerificationOtp` | verify thành công (DEL) |
| `otp:cooldown:{email}` | 1 | 30s | `resendOtp` | chỉ TTL |
| `otp:attempt:{email}` | 1 | 15m | `verifyEmailWithOtp` | verify thành công (DEL) |
| `pwreset:{token}` | 1 | 15m | `forgotPassword` | reset thành công (DEL) |
| `denylist:jti:{jti}` | 1 | TTL còn lại của token | `logout` | chỉ TTL |
| `leaderboard::*`, `myRank::{userId}` | 0 | 60s | `LeaderboardCacheService` | chỉ TTL |
| `user:{id}:counts` | 0 | 15m | `enrichProfileResponse` | follow/unfollow, post create/delete |
| `notif:unread:{userId}` | 0 | 5m | `countUnread` | markAsRead, tạo notification |
| `userByKeycloak::{kcId}` | 0 | 30m | `resolveUserId` | lock/unlock/xoá user |
| `geoInVietnam::{lat3}:{lon3}` | 0 | 30d | `isLocationInVietnam` | không bao giờ |
| `geoNearby::{lat3}:{lon3}:{r}:{status}` | 0 | 30m | `findNearbyHotspotsWithStatus` | CRUD hotspot |
| `hotspotDetail::{id}` … | 0 | 15m | detail service | curator sửa nội dung |

---

# Kiểm chứng chung

**Soi key** (dùng `SCAN`, **không dùng `KEYS *`** trên môi trường thật):
```
docker exec -it culture-quest-redis redis-cli -a "mat-khau"

SCAN 0 MATCH "post:*" COUNT 100
TTL otp:cooldown:test@example.com
HGETALL post:1:counts
INFO stats          # keyspace_hits / keyspace_misses → tỉ lệ hit
SELECT 1            # chuyển sang DB auth
MONITOR             # xem lệnh realtime khi debug (chỉ dùng ngắn, tốn hiệu năng)
```

**Đo trước/sau:** bật tạm `spring.jpa.show-sql: true`, gọi `GET /api/admin/dashboard` 2 lần — lần 2 phải **không có SQL nào**. Làm tương tự `/api/posts/newsfeed`, `/api/tags`.

**Unit test:** dự án hiện chỉ có 14 test Mockito (`@ExtendWith(MockitoExtension.class)`, `@MockitoSettings(LENIENT)`, `@Nested`, `@DisplayName` tiếng Việt), **không có `src/test/resources`**. Cách khớp hiện trạng: mock `RedisTemplate`/`StringRedisTemplate` như dependency thường, test logic cache-aside (hit → không gọi repository; miss → gọi repository rồi ghi cache).

Khi mock `RedisTemplate`, nhớ stub cả `opsForValue()` / `opsForHash()`:
```java
@Mock private RedisTemplate<String, Object> redisTemplate;
@Mock private ValueOperations<String, Object> valueOps;

@BeforeEach
void setUp() {
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
}
```

**Không** thêm Testcontainers ở đợt này — làm chậm build, mà CI hiện chưa hề chạy `mvn test`.

`BackEndApplicationTests` không gãy nhờ default `${REDIS_HOST:localhost}` ở Bước 3 (Lettuce kết nối lười, chỉ fail khi thực sự gọi lệnh).

---

# Thứ tự làm

**Bước 1–6 (Phase 0)** → **7 (Phase 1)** → **8–10 (Phase 2)** → **11 (Phase 3)** → **12–13 (Phase 6)** → **14 (Phase 4)** → **15–17 (Phase 5)** → **18 (Phase 7)** → **19 (docs)**

Phase 6 làm trước Phase 4/5 vì nó thuần `@Cacheable` (vùng an toàn). Bước 17 (JWT denylist) rủi ro nhất — làm cuối, test kỹ luồng login/logout.

Mỗi phase là một commit riêng, verify xong mới sang phase kế tiếp.
