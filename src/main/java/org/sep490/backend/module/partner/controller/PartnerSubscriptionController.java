package org.sep490.backend.module.partner.controller;

import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.admin.dto.request.PartnerSubscriptionRequest;
import org.sep490.backend.module.admin.dto.response.PartnerSubscriptionResponse;
import org.sep490.backend.module.admin.dto.response.PaymentInitResponse;
import org.sep490.backend.module.admin.dto.response.SubscriptionPlanResponse;
import org.sep490.backend.module.admin.service.PartnerSubscriptionService;
import org.sep490.backend.module.admin.service.SubscriptionPlanService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/partner")
@RequiredArgsConstructor
public class PartnerSubscriptionController {

    private final PartnerSubscriptionService subscriptionService;
    private final SubscriptionPlanService subscriptionPlanService;

    @PostMapping(value = "/subscriptions/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PartnerSubscriptionResponse> registerSubscription(
            @ModelAttribute PartnerSubscriptionRequest request) {
        PartnerSubscriptionResponse response = subscriptionService.registerSubscription(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/subscriptions/my")
    public ResponseEntity<List<PartnerSubscriptionResponse>> getMySubscriptions() {
        return ResponseEntity.ok(subscriptionService.getMySubscriptions());
    }

    @GetMapping("/subscriptions/{id}")
    public ResponseEntity<SubscriptionPlanResponse> getSubscriptionPlanDetail(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionPlanService.getSubscriptionPlanDetail(id));
    }

    @GetMapping("/{id}/subscriptions")
    public ResponseEntity<List<PartnerSubscriptionResponse>> getSubscriptionsByPartnerId(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.getSubscriptionsByPartnerId(id));
    }

    @PostMapping("/subscriptions/{id}/initiate-payment")
    public ResponseEntity<PaymentInitResponse> initiatePayment(
            @PathVariable Long id,
            @RequestParam(required = false) String redirectUrl,
            @RequestParam(defaultValue = "PAYOS") String gateway) {
        return ResponseEntity.ok(subscriptionService.initiatePayment(id, redirectUrl, gateway));
    }
}
