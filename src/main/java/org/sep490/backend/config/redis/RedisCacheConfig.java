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
            @Qualifier("redisObjectMapper") ObjectMapper redisObjectMapper
            ) {
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
        configs.put(CacheNames.PARTNER_DASHBOARD, base.entryTtl(CacheNames.TTL_DASHBOARD));
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
        configs.put(CacheNames.ROLE_PERMISSIONS, base.entryTtl(CacheNames.TTL_PERMISSIONS));
        configs.put(CacheNames.USER_PERMISSION_OVERRIDES, base.entryTtl(CacheNames.TTL_USER_OVERRIDES));
        configs.put(CacheNames.USER_ENTITLEMENTS, base.entryTtl(CacheNames.TTL_ENTITLEMENTS));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(configs)
                .build();
    }
}
