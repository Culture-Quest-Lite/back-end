package org.sep490.backend.module.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.user.dto.request.UpdateUserRoleRequest;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.sep490.backend.common.dto.BaseFilterRequest;
import org.sep490.backend.module.user.dto.response.UserProfileResponse;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    @GetMapping("/users")
    public ResponseEntity<Page<UserProfileResponse>> getAllUsers(
            @ParameterObject @ModelAttribute BaseFilterRequest filterRequest
    ) {
        Page<UserProfileResponse> response = userService.getAllUsersWithFilter(filterRequest);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/lock")
    public ResponseEntity<Map<String, String>> lockUser(@PathVariable Long id) {
        userService.lockUser(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Đã khóa tài khoản người dùng thành công");
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/unlock")
    public ResponseEntity<Map<String, String>> unlockUser(@PathVariable Long id) {
        userService.unlockUser(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Đã mở khóa tài khoản người dùng thành công");
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<Map<String, String>> updateUserRole(
            @PathVariable Long id,
            @RequestBody @Valid UpdateUserRoleRequest request
    ) {
        userService.updateUserRole(id, request.getRoles());
        Map<String, String> response = new HashMap<>();
        response.put("message", "Cập nhật vai trò người dùng thành công");
        return ResponseEntity.ok(response);
    }
}
