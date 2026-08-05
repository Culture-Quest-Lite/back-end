package org.sep490.backend.module.user.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.config.redis.CacheNames;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.user.service.UserIdCacheService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tách khỏi UserServiceImpl để @Cacheable đi qua proxy (self-invocation không có tác dụng).
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserIdCacheServiceImpl implements UserIdCacheService {

    UserRepository userRepository;

    /** null không được cache (disableCachingNullValues trong RedisCacheConfig). */
    @Override
    @Cacheable(value = CacheNames.USER_BY_KEYCLOAK, key = "#keycloakUserId")
    @Transactional(readOnly = true)
    public Long resolveUserId(String keycloakUserId) {
        return userRepository.findByKeycloakUserId(keycloakUserId)
                .map(User::getUserId)
                .orElse(null);
    }

    @Override
    @CacheEvict(value = CacheNames.USER_BY_KEYCLOAK, key = "#keycloakUserId")
    public void evict(String keycloakUserId) {
        // Thân rỗng: chỉ để kích hoạt @CacheEvict qua proxy
    }
}
