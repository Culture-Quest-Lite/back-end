package org.sep490.backend.module.partner.controller;

import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.admin.service.PartnerSubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/payment/payos")
@RequiredArgsConstructor
public class PayOsPaymentController {
    private final PartnerSubscriptionService subscriptionService;

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> handleWebhook(@RequestBody Map<String, Object> payload) {
        subscriptionService.handlePayOsWebhook(payload);
        return ResponseEntity.ok(Map.of("code", "00", "desc", "success"));
    }
}
