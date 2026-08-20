package org.sep490.backend.module.content.service.inter;

import org.sep490.backend.module.content.dto.record.CultureCheckResult;

public interface CultureGuardService {

    String KIND_TAG = "TAG";
    String KIND_STORY = "STORY";

    CultureCheckResult check(String kind, String text);

    CultureCheckResult checkAndEnforce(String kind, String text, Boolean confirmCultural);
}
