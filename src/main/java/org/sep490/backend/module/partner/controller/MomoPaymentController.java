package org.sep490.backend.module.partner.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.module.admin.dto.request.MomoIpnRequest;
import org.sep490.backend.module.admin.service.PartnerSubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payment/momo")
@RequiredArgsConstructor
public class MomoPaymentController {

    private final PartnerSubscriptionService subscriptionService;

    @PostMapping("/ipn")
    public ResponseEntity<Map<String, String>> handleIpn(
            @RequestBody MomoIpnRequest ipnRequest) {
        subscriptionService.handleMomoIpn(ipnRequest);
        return ResponseEntity.ok(Map.of("message", "Received"));
    }
}
