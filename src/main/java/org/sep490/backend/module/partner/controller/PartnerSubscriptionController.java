package org.sep490.backend.module.partner.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.admin.dto.request.PartnerSubscriptionRequest;
import org.sep490.backend.module.admin.dto.response.PartnerSubscriptionResponse;
import org.sep490.backend.module.admin.service.PartnerSubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscription")
@RequiredArgsConstructor
public class PartnerSubscriptionController {

    private final PartnerSubscriptionService subscriptionService;

    @PostMapping("/register")
    public ResponseEntity<PartnerSubscriptionResponse> registerSubscription(
            @Valid @RequestBody PartnerSubscriptionRequest request
    ) {
        PartnerSubscriptionResponse response = subscriptionService.registerSubscription(request);
        return ResponseEntity.ok(response);
    }
}
