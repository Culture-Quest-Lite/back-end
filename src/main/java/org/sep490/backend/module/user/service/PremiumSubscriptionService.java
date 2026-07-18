package org.sep490.backend.module.user.service;

import org.sep490.backend.module.admin.dto.response.PaymentInitResponse;
import org.sep490.backend.module.user.dto.request.PremiumSubscribeRequest;
import org.sep490.backend.module.user.dto.response.PremiumSubscriptionResponse;

import java.util.List;

public interface PremiumSubscriptionService {
    PaymentInitResponse subscribe(PremiumSubscribeRequest request);
    List<PremiumSubscriptionResponse> getMyPremiumSubscription();
}
