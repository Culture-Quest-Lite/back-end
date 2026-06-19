package org.sep490.backend.module.admin.service;

import org.sep490.backend.module.admin.dto.request.PartnerSubscriptionRequest;
import org.sep490.backend.module.admin.dto.response.PartnerSubscriptionResponse;

public interface PartnerSubscriptionService {
    PartnerSubscriptionResponse registerSubscription(PartnerSubscriptionRequest request);
    PartnerSubscriptionResponse verifiedSubscription(Long subscriptionId, boolean isVerified);
    java.util.List<PartnerSubscriptionResponse> getMySubscriptions();
    java.util.List<PartnerSubscriptionResponse> getSubscriptionsByPartnerId(Long partnerId);
}
