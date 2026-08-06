package org.sep490.backend.module.authorization.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionResponse {
    private Long permissionId;
    private String code;
    private String groupName;
    private String description;
    private boolean active;
}
