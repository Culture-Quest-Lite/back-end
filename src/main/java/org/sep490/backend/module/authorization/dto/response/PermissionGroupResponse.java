package org.sep490.backend.module.authorization.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionGroupResponse {
    private String groupName;
    private List<PermissionResponse> permissions;
}
