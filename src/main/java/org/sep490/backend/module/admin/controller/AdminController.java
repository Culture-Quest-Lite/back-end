package org.sep490.backend.module.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.admin.dto.response.PartnerSubscriptionResponse;
import org.sep490.backend.module.admin.entity.enumeration.InvoiceStatus;
import org.sep490.backend.module.admin.service.PartnerSubscriptionService;
import org.sep490.backend.module.social.dto.request.DeletePostRequest;
import org.sep490.backend.module.social.dto.request.RejectPostRequest;
import org.sep490.backend.module.social.dto.response.PostResponse;
import org.sep490.backend.module.social.service.PostService;
import org.sep490.backend.module.user.dto.request.UpdateUserRoleRequest;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.sep490.backend.common.filter.dto.BaseFilterRequest;
import org.sep490.backend.module.user.dto.response.UserProfileResponse;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final PostService postService;
    private final PartnerSubscriptionService partnerSubscriptionService;

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

    @PutMapping("/post/{id}/approve")
    public ResponseEntity<PostResponse> approvePost(@PathVariable Long id) {
        PostResponse response = postService.approvePost(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/post/{id}/reject")
    public ResponseEntity<PostResponse> rejectPost(
            @PathVariable Long id,
            @RequestBody @Valid RejectPostRequest  request
    ) {
        PostResponse response = postService.rejectPost(id, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/ban")
    public ResponseEntity<PostResponse> banPost(
            @PathVariable Long id,
            @RequestBody @Valid DeletePostRequest request
            ) {
        PostResponse response = postService.banPostByAdmin(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<List<PartnerSubscriptionResponse>> getAllSubscriptions(
            @RequestParam(required = false) InvoiceStatus status) {
        List<PartnerSubscriptionResponse> response = partnerSubscriptionService.getAllSubscriptions(status);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/subscription/{id}/verify")
    public ResponseEntity<PartnerSubscriptionResponse> verifiedSubscription(
            @PathVariable Long id,
            @RequestParam boolean isApproved) {
        PartnerSubscriptionResponse response = partnerSubscriptionService.verifiedSubscription(id, isApproved);
        return ResponseEntity.ok(response);
    }
}