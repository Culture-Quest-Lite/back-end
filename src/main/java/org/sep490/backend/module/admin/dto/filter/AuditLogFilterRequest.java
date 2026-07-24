package org.sep490.backend.module.admin.dto.filter;

import lombok.Data;
import org.sep490.backend.module.admin.entity.enumeration.AuditAction;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class AuditLogFilterRequest {
    private String search;
    private Long userId;
    private AuditAction action;
    private String tableName;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime to;

    private int page = 0;
    private int size = 20;
    private String sortBy = "createdAt";
    private String sortDir = "desc";
}
