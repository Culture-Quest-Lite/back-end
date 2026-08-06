package org.sep490.backend.module.authorization.service;

import org.sep490.backend.module.authorization.dto.request.GrantUserPermissionRequest;
import org.sep490.backend.module.authorization.dto.response.PermissionGroupResponse;
import org.sep490.backend.module.authorization.dto.response.RolePermissionMatrixResponse;
import org.sep490.backend.module.authorization.dto.response.UserPermissionResponse;
import org.sep490.backend.module.user.entity.enumeration.UserRole;

import java.util.List;

public interface PermissionAdminService {
    List<PermissionGroupResponse> listGrouped();
    RolePermissionMatrixResponse getMatrix();
    void replacePermissions(UserRole role, List<String> codes);
    void grant(UserRole role, String code);
    void revoke(UserRole role, String code);

    List<UserPermissionResponse> getUserPermissions(Long userId);
    void upsertUserPermission(Long userId, GrantUserPermissionRequest request);
    void deleteUserPermission(Long userId, String code);
}
