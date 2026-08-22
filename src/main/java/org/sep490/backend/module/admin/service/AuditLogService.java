package org.sep490.backend.module.admin.service;

import org.sep490.backend.module.admin.dto.filter.AuditLogFilterRequest;
import org.sep490.backend.module.admin.dto.response.AuditLogResponse;
import org.sep490.backend.module.admin.entity.enumeration.AuditAction;
import org.springframework.data.domain.Page;

public interface AuditLogService {
    void log(AuditAction action, String tableName, String recordId, Object oldValue, Object newValue);
    void logWithEndpoint(AuditAction action, String tableName, String recordId,
                         Object oldValue, Object newValue, String endpoint);
    Page<AuditLogResponse> getLogs(AuditLogFilterRequest filter);
}
