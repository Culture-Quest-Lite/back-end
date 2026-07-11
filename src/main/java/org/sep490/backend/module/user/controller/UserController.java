package org.sep490.backend.module.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.gamification.dto.response.RewardTransactionResponse;
import org.sep490.backend.module.gamification.service.RewardTransactionService;
import org.sep490.backend.module.partner.dto.filter.VoucherFilter;
import org.sep490.backend.module.partner.dto.response.VoucherUsageResponse;
import org.sep490.backend.module.partner.service.VoucherService;
import org.sep490.backend.module.social.dto.response.PostResponse;
import org.sep490.backend.module.social.service.PostService;
import org.sep490.backend.module.user.dto.request.UpdateProfileRequest;
import org.sep490.backend.module.user.dto.response.FollowUserResponse;
import org.sep490.backend.module.user.dto.response.UserProfileResponse;
import org.sep490.backend.module.user.service.UserService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PostService postService;
    private final VoucherService voucherService;
    private final RewardTransactionService rewardTransactionService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @AuthenticationPrincipal Jwt jwt
            ) {
        String keycloakUserId = jwt.getSubject();
        UserProfileResponse response = userService.getMyProfile(keycloakUserId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserProfileResponse> getProfile(
            @PathVariable Long id
    ) {
        UserProfileResponse response = userService.getProfile(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid UpdateProfileRequest request
            ) {
        String keycloakUserId = jwt.getSubject();
        UserProfileResponse response = userService.updateMyProfile(keycloakUserId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/follow")
    public ResponseEntity<Map<String, String>> followUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        String keycloakUserId = jwt.getSubject();
        userService.followUser(keycloakUserId, id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Theo dõi người dùng thành công");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/follow")
    public ResponseEntity<Map<String, String>> unfollowUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        String keycloakUserId = jwt.getSubject();
        userService.unfollowUser(keycloakUserId, id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Đã hủy theo dõi người dùng");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/followers")
    public ResponseEntity<List<FollowUserResponse>> getFollowers(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(userService.getFollowers(id));
    }

    @GetMapping("/{id}/followings")
    public ResponseEntity<List<FollowUserResponse>> getFollowings(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(userService.getFollowings(id));
    }

    @GetMapping("/user/{id}/posts")
    public ResponseEntity<Slice<PostResponse>> getPostsByUserId(
            @PathVariable("id") Long userId,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt",
            direction = Sort.Direction.DESC) Pageable pageable
            ) {
        Slice<PostResponse> responses = postService.getPostsByUserId(userId, pageable);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/my-vouchers")
    public ResponseEntity<Page<VoucherUsageResponse>> getMyRedeemedVouchers(
            @Valid @ParameterObject @ModelAttribute VoucherFilter filter) {
        return ResponseEntity.ok(voucherService.getMyRedeemedVouchers(filter));
    }

    @GetMapping("/history")
    public ResponseEntity<Page<RewardTransactionResponse>> getMyPointHistory(
            @Valid @ParameterObject @ModelAttribute VoucherFilter filter) {
        return ResponseEntity.ok(rewardTransactionService.getMyRewardHistory(filter));
    }
}
