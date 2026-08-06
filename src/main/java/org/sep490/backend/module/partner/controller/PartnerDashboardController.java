package org.sep490.backend.module.partner.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.partner.dto.response.PartnerDashboardResponse;
import org.sep490.backend.module.partner.service.PartnerDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/partner/dashboard")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PartnerDashboardController {

    PartnerDashboardService partnerDashboardService;

    @GetMapping
    @PreAuthorize("hasRole('PARTNER') and hasAuthority('PERM_DASHBOARD_PARTNER_VIEW')")
    public ResponseEntity<PartnerDashboardResponse> getDashboard() {
        return ResponseEntity.ok(partnerDashboardService.getDashboard());
    }
}
