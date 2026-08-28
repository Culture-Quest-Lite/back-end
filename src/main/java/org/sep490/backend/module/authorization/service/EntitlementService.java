package org.sep490.backend.module.authorization.service;

public interface EntitlementService {
    boolean can(String ruleKey);
    boolean withinQuota(String ruleKey, long used);
    boolean isPremium();
}
