package org.sep490.backend.module.authorization.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sep490.backend.module.user.entity.enumeration.UserRole;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionMatrixResponse {
    private List<PermissionGroupResponse> allPermissions;
    private Map<UserRole, Set<String>> matrix;
}
