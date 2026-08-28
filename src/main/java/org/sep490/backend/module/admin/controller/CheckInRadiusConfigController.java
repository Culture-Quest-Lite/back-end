package org.sep490.backend.module.admin.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.admin.annotation.Auditable;
import org.sep490.backend.module.admin.dto.request.CheckInRadiusConfigRequest;
import org.sep490.backend.module.admin.dto.response.CheckInRadiusConfigResponse;
import org.sep490.backend.module.admin.entity.enumeration.AuditAction;
import org.sep490.backend.module.admin.service.CheckInRadiusConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Cấu hình bán kính check-in. Admin đặt khoảng cho phép, curator chỉ đọc để
 * biết giới hạn khi tạo hotspot.
 */
@RestController
@RequestMapping("/api/v1/configs/check-in-radius")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CheckInRadiusConfigController {

    CheckInRadiusConfigService checkInRadiusConfigService;

    @GetMapping
    public ResponseEntity<CheckInRadiusConfigResponse> getConfig() {
        return ResponseEntity.ok(checkInRadiusConfigService.getConfig());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('PERM_SYSTEM_CONFIG_MANAGE')")
    @Auditable(value = AuditAction.UPDATE_CHECK_IN_RADIUS_CONFIG, tableName = "check_in_radius_config")
    public ResponseEntity<CheckInRadiusConfigResponse> updateConfig(
            @RequestBody @Valid CheckInRadiusConfigRequest request) {
        return ResponseEntity.ok(checkInRadiusConfigService.updateConfig(request));
    }
}
