package org.sep490.backend.module.authorization.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserPermissionResponse {
    private Long id;
    private String code;
    private String description;
    private boolean granted;
    private LocalDateTime expiresAt;
    private String reason;
    private boolean expired;

}
