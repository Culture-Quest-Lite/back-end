package org.sep490.backend.module.authorization.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.admin.annotation.Auditable;
import org.sep490.backend.module.admin.entity.enumeration.AuditAction;
import org.sep490.backend.module.authorization.dto.request.GrantUserPermissionRequest;
import org.sep490.backend.module.authorization.dto.request.UpdateRolePermissionRequest;
import org.sep490.backend.module.authorization.dto.response.PermissionGroupResponse;
import org.sep490.backend.module.authorization.dto.response.RolePermissionMatrixResponse;
import org.sep490.backend.module.authorization.dto.response.UserPermissionResponse;
import org.sep490.backend.module.authorization.service.PermissionAdminService;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_PERMISSION_MANAGE')")
public class PermissionAdminController {

    private final PermissionAdminService permissionAdminService;

    @GetMapping
    public ResponseEntity<List<PermissionGroupResponse>> list() {
        return ResponseEntity.ok(permissionAdminService.listGrouped());
    }

    @GetMapping("/matrix")
    public ResponseEntity<RolePermissionMatrixResponse> matrix() {
        return ResponseEntity.ok(permissionAdminService.getMatrix());
    }

    @PutMapping("/matrix/{role}")
    @Auditable(value = AuditAction.UPDATE_ROLE_PERMISSION, tableName = "role_permissions")
    public ResponseEntity<Map<String, String>> updateRole(
            @PathVariable UserRole role,
            @RequestBody @Valid UpdateRolePermissionRequest request) {
        permissionAdminService.replacePermissions(role, request.getCodes());
        return ResponseEntity.ok(Map.of("message", "Cập nhật phân quyền thành công"));
    }

    @PostMapping("/roles/{role}/{code}")
    @Auditable(value = AuditAction.UPDATE_ROLE_PERMISSION, tableName = "role_permissions")
    public ResponseEntity<Map<String, String>> grantToRole(
            @PathVariable UserRole role, @PathVariable String code) {
        permissionAdminService.grant(role, code);
        return ResponseEntity.ok(Map.of("message", "Đã cấp quyền " + code + " cho " + role));
    }

    @DeleteMapping("/roles/{role}/{code}")
    @Auditable(value = AuditAction.UPDATE_ROLE_PERMISSION, tableName = "role_permissions")
    public ResponseEntity<Map<String, String>> revokeFromRole(
            @PathVariable UserRole role, @PathVariable String code) {
        permissionAdminService.revoke(role, code);
        return ResponseEntity.ok(Map.of("message", "Đã gỡ quyền " + code + " khỏi " + role));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<List<UserPermissionResponse>> getUserPermissions(@PathVariable Long userId) {
        return ResponseEntity.ok(permissionAdminService.getUserPermissions(userId));
    }

    @PutMapping("/users/{userId}")
    @Auditable(value = AuditAction.UPDATE_USER_PERMISSION, tableName = "user_permissions")
    public ResponseEntity<Map<String, String>> upsertUserPermission(
            @PathVariable Long userId,
            @RequestBody @Valid GrantUserPermissionRequest request) {
        permissionAdminService.upsertUserPermission(userId, request);
        return ResponseEntity.ok(Map.of("message",
                (request.getGranted() ? "Đã cấp" : "Đã thu hồi") + " quyền " + request.getCode()));
    }

    @DeleteMapping("/users/{userId}/{code}")
    @Auditable(value = AuditAction.UPDATE_USER_PERMISSION, tableName = "user_permissions")
    public ResponseEntity<Map<String, String>> deleteUserPermission(
            @PathVariable Long userId, @PathVariable String code) {
        permissionAdminService.deleteUserPermission(userId, code);
        return ResponseEntity.ok(Map.of("message", "Đã xoá ngoại lệ quyền " + code));
    }
}
