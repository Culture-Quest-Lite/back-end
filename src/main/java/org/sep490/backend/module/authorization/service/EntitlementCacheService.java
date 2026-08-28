package org.sep490.backend.module.authorization.service;

import java.util.Map;

public interface EntitlementCacheService {
    Map<String, String> getRules(Long userId);
    void evict(Long userId);
}
