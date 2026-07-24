package org.sep490.backend.module.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.admin.dto.response.PaymentInitResponse;
import org.sep490.backend.module.admin.dto.response.SubscriptionPlanResponse;
import org.sep490.backend.module.admin.entity.enumeration.PlanType;
import org.sep490.backend.module.admin.service.SubscriptionPlanService;
import org.sep490.backend.module.user.dto.request.PremiumSubscribeRequest;
import org.sep490.backend.module.user.dto.response.PremiumSubscriptionResponse;
import org.sep490.backend.module.user.service.PremiumSubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user/premium")
@RequiredArgsConstructor
public class PremiumSubscriptionController {

    private final PremiumSubscriptionService premiumSubscriptionService;
    private final SubscriptionPlanService subscriptionPlanService;

    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlanResponse>> getPremiumPlans() {
        return ResponseEntity.ok(subscriptionPlanService.getActivePlanByType(PlanType.PREMIUM));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<PaymentInitResponse> subscribe(@Valid @RequestBody PremiumSubscribeRequest request) {
        return ResponseEntity.ok(premiumSubscriptionService.subscribe(request));
    }

    @GetMapping("/my")
    public ResponseEntity<List<PremiumSubscriptionResponse>> getMyPremiumSubscriptions() {
        return ResponseEntity.ok(premiumSubscriptionService.getMyPremiumSubscription());
    }
}
