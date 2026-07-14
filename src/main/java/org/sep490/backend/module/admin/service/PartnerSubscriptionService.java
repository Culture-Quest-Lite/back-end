package org.sep490.backend.module.admin.service;

import org.sep490.backend.module.admin.dto.request.MomoIpnRequest;
import org.sep490.backend.module.admin.dto.request.PartnerSubscriptionRequest;
import org.sep490.backend.module.admin.dto.response.MomoPaymentInitResponse;
import org.sep490.backend.module.admin.dto.response.PartnerSubscriptionResponse;
import org.sep490.backend.module.admin.dto.response.PaymentInitResponse;

import java.util.List;
import java.util.Map;

public interface PartnerSubscriptionService {
    PartnerSubscriptionResponse registerSubscription(PartnerSubscriptionRequest request);
    PartnerSubscriptionResponse verifiedSubscription(Long subscriptionId, boolean isVerified);
    List<PartnerSubscriptionResponse> getMySubscriptions();
    List<PartnerSubscriptionResponse> getSubscriptionsByPartnerId(Long partnerId);

    MomoPaymentInitResponse initiatePayment(Long subscriptionId, String redirectUrl);
    void handleMomoIpn(MomoIpnRequest request);

    PaymentInitResponse initiatePayment(Long subscriptionId, String redirectUrl, String gateway);
    void handlePayOsWebhook(Map<String, Object> body);
}
