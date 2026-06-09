package org.sep490.backend.module.user.controller;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.user.dto.request.UpdateProfileRequest;
import org.sep490.backend.module.user.dto.response.UserProfileResponse;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @AuthenticationPrincipal Jwt jwt
            ) {
        String keycloakUserId = jwt.getSubject();
        UserProfileResponse response = userService.getMyProfile(keycloakUserId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/{id}")
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
}
