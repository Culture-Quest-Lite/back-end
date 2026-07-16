package org.sep490.backend.module.exploration.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.exploration.dto.request.UserHotspotProgressRequest;
import org.sep490.backend.module.exploration.dto.response.UserHotspotProgressResponse;
import org.sep490.backend.module.exploration.service.inter.UserHotspotProgressService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user-hotspot-progress")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserHotspotProgressController {

    UserHotspotProgressService userHotspotProgressService;

    @PostMapping
    public ResponseEntity<UserHotspotProgressResponse> checkIn(@Valid @RequestBody UserHotspotProgressRequest request) {
        UserHotspotProgressResponse response = userHotspotProgressService.checkIn(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
