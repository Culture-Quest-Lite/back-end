package org.sep490.backend.config.redis;

import io.lettuce.core.SocketOptions;
import io.lettuce.core.ClientOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Configuration
public class AuthRedisConfig {

    @Bean(name = "authRedisConnectionFactory", autowireCandidate = false)
    public RedisConnectionFactory authRedisConnectionFactory(
            RedisProperties properties,
            @Value("${app.redis.auth-database:1}") int authDatabase
    ) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(properties.getHost());
        config.setPort(properties.getPort());
        config.setDatabase(authDatabase);
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            config.setPassword(properties.getPassword());
        }

        Duration commandTimeout = properties.getTimeout() != null
                ? properties.getTimeout()
                : Duration.ofSeconds(2);
        Duration connectTimeout = properties.getConnectTimeout() != null
                ? properties.getConnectTimeout()
                : Duration.ofSeconds(2);

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(commandTimeout)
                .clientOptions(ClientOptions.builder()
                        .socketOptions(SocketOptions.builder()
                                .connectTimeout(connectTimeout)
                                .build())
                        .build())
                .build();

        return new LettuceConnectionFactory(config, clientConfig);
    }
    
    @Bean("authRedisTemplate")
    public StringRedisTemplate authRedisTemplate(
            RedisProperties properties,
            @Value("${app.redis.auth-database:1}") int authDatabase
    ) {
        return new StringRedisTemplate(authRedisConnectionFactory(properties, authDatabase));
    }
}
