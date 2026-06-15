package org.sep490.backend.module.partner.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.admin.dto.request.PartnerSubscriptionRequest;
import org.sep490.backend.module.admin.dto.response.PartnerSubscriptionResponse;
import org.sep490.backend.module.admin.dto.response.SubscriptionPlanResponse;
import org.sep490.backend.module.admin.service.PartnerSubscriptionService;
import org.sep490.backend.module.admin.service.SubscriptionPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/partner/subscriptions")
@RequiredArgsConstructor
public class PartnerSubscriptionController {

    private final PartnerSubscriptionService subscriptionService;
    private final SubscriptionPlanService subscriptionPlanService;

    @PostMapping("/register")
    public ResponseEntity<PartnerSubscriptionResponse> registerSubscription(
            @Valid @RequestBody PartnerSubscriptionRequest request
    ) {
        PartnerSubscriptionResponse response = subscriptionService.registerSubscription(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionPlanResponse> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionPlanService.getSubscriptionPlanDetail(id));
    }
}
