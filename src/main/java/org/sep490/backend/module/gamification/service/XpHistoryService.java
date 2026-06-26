package org.sep490.backend.module.gamification.service;

import org.sep490.backend.module.gamification.dto.request.XpHistoryRequest;
import org.sep490.backend.module.gamification.entity.XpHistory;

public interface XpHistoryService {
    void create(XpHistoryRequest request);
}
