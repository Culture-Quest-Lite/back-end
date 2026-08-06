package org.sep490.backend.module.authorization.service.impl;

import lombok.RequiredArgsConstructor;
import org.sep490.backend.config.redis.CacheNames;
import org.sep490.backend.module.admin.entity.PlanRule;
import org.sep490.backend.module.admin.repository.PlanRuleRepository;
import org.sep490.backend.module.authorization.service.EntitlementCacheService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EntitlementCacheServiceImpl implements EntitlementCacheService {

    private final PlanRuleRepository planRuleRepository;

    @Override
    @Cacheable(value = CacheNames.USER_ENTITLEMENTS, key = "#userId")
    @Transactional(readOnly = true)
    public Map<String, String> getRules(Long userId) {
        Map<String, String> rules = new HashMap<>();
        for (PlanRule rule : planRuleRepository.findActiveRulesByUserId(userId)) {
            rules.putIfAbsent(rule.getRuleKey(), rule.getRuleValue());
        }
        return rules;
    }

    @Override
    @CacheEvict(value = CacheNames.USER_ENTITLEMENTS, key = "#userId")
    public void evict(Long userId) {
        //Thân rỗng dùng để kích hoạt @CacheEvict qua proxy
    }
}
