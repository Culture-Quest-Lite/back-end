package org.sep490.backend.module.curator.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.curator.dto.response.CuratorDashboardResponse;
import org.sep490.backend.module.curator.service.CuratorDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/curator/dashboard")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CuratorDashboardController {

    CuratorDashboardService curatorDashboardService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_DASHBOARD_CURATOR_VIEW')")
    public ResponseEntity<CuratorDashboardResponse> getDashboard() {
        return ResponseEntity.ok(curatorDashboardService.getDashboard());
    }
}
