package org.sep490.backend.module.authorization.service.impl;

import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.authorization.service.EntitlementCacheService;
import org.sep490.backend.module.authorization.service.EntitlementService;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service("entitlement")
@RequiredArgsConstructor
public class EntitlementServiceImpl implements EntitlementService {

    private final UserService userService;
    private final EntitlementCacheService cacheService;

    @Override
    @Transactional(readOnly = true)
    public boolean can(String ruleKey) {
        return Boolean.parseBoolean(currentRules().getOrDefault(ruleKey, "false"));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean withinQuota(String ruleKey, long used) {
        String limit = currentRules().get(ruleKey);
        if (limit == null) return false;
        try {
            return used < Long.parseLong(limit);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPremium() {
        return Boolean.TRUE.equals(userService.getCurrentUser().getIsPremium());
    }

    private Map<String, String> currentRules() {
        return cacheService.getRules(userService.getCurrentUser().getUserId());
    }
}
