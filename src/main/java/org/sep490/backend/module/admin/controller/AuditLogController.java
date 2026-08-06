package org.sep490.backend.module.admin.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.admin.dto.filter.AuditLogFilterRequest;
import org.sep490.backend.module.admin.dto.response.AuditLogResponse;
import org.sep490.backend.module.admin.service.AuditLogService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuditLogController {

    AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_AUDIT_LOG_VIEW')")
    public ResponseEntity<Page<AuditLogResponse>> getLogs(
            @Valid @ParameterObject @ModelAttribute AuditLogFilterRequest filter) {
        return ResponseEntity.ok(auditLogService.getLogs(filter));
    }
}
