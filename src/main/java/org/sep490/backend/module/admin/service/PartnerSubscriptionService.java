package org.sep490.backend.module.admin.service;

import org.sep490.backend.module.admin.dto.request.PartnerSubscriptionRequest;
import org.sep490.backend.module.admin.dto.response.PartnerSubscriptionResponse;

public interface PartnerSubscriptionService {
    PartnerSubscriptionResponse registerSubscription(PartnerSubscriptionRequest request);
}
