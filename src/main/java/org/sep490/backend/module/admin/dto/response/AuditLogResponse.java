package org.sep490.backend.module.admin.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sep490.backend.module.admin.entity.enumeration.AuditAction;
import org.sep490.backend.module.user.entity.enumeration.UserRole;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogResponse {

    private Long logId;
    private ActorResponse actor;
    private AuditAction action;
    private String tableName;
    private String recordId;
    private String endpoint;
    private JsonNode oldValue;
    private JsonNode newValue;
    private String ipAddress;
    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ActorResponse {
        private Long userId;
        private String username;
        private String displayName;
        private UserRole role;
    }
}
