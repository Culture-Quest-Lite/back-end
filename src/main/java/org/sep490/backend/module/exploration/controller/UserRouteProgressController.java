package org.sep490.backend.module.exploration.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.exploration.dto.filter.UserRouteProgressFilter;
import org.sep490.backend.module.exploration.dto.response.UserRouteProgressDetailResponse;
import org.sep490.backend.module.exploration.dto.response.UserRouteProgressResponse;
import org.sep490.backend.module.exploration.service.inter.UserRouteProgressService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/user-route-progress")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserRouteProgressController {

    UserRouteProgressService userRouteProgressService;

    @PostMapping("/start/{id}")
    public ResponseEntity<UserRouteProgressResponse> start(@PathVariable("id") Long routeId) {
        HashMap<Integer, UserRouteProgressResponse> response = userRouteProgressService.startRouteProgress(routeId);
        return response.containsKey(201)
                ? ResponseEntity.status(HttpStatus.CREATED).body(response.get(201))
                : ResponseEntity.ok(response.get(200));
    }

    @PutMapping("/abandon/{id}")
    public ResponseEntity<UserRouteProgressResponse> abandon(@PathVariable("id") Long routeId) {
        UserRouteProgressResponse response = userRouteProgressService.abandonRouteProgress(routeId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<UserRouteProgressResponse>> getAll(@ModelAttribute UserRouteProgressFilter filter) {
        Page<UserRouteProgressResponse> response = userRouteProgressService.getAll(filter);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserRouteProgressDetailResponse> getById(@PathVariable("id") Long userRouteProgressId) {
        UserRouteProgressDetailResponse response = userRouteProgressService.getRouteProgress(userRouteProgressId);
        return ResponseEntity.ok(response);
    }

}
