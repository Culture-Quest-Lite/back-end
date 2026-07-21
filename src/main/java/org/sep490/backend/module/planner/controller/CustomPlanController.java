package org.sep490.backend.module.planner.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.planner.dto.request.CreateCustomPlanRequest;
import org.sep490.backend.module.planner.dto.request.DescriptionSuggestRequest;
import org.sep490.backend.module.planner.dto.request.NearbySuggestRequest;
import org.sep490.backend.module.planner.dto.request.OptimizeRouteRequest;
import org.sep490.backend.module.planner.dto.response.HotspotSuggestionResponse;
import org.sep490.backend.module.planner.dto.response.OptimizedRouteResponse;
import org.sep490.backend.module.planner.dto.response.UserPlanResponse;
import org.sep490.backend.module.planner.service.AISuggestionService;
import org.sep490.backend.module.planner.service.RouteOptimizationService;
import org.sep490.backend.module.planner.service.UserPlanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/custom-plans")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CustomPlanController {

    AISuggestionService aiSuggestionService;
    RouteOptimizationService routeOptimizationService;
    UserPlanService userPlanService;

    @PostMapping("/suggest-by-description")
    public ResponseEntity<List<HotspotSuggestionResponse>> suggestByDescription(@Valid @RequestBody DescriptionSuggestRequest request) {
        return ResponseEntity.ok(aiSuggestionService.suggestByDescription(request));
    }

    @PostMapping("/suggest-nearby")
    public ResponseEntity<List<HotspotSuggestionResponse>> suggestNearby(
            @Valid @RequestBody NearbySuggestRequest request
            ) {
        return ResponseEntity.ok(aiSuggestionService.suggestNearby(request));
    }

    @PostMapping("/optimize")
    public ResponseEntity<OptimizedRouteResponse> optimize(
            @Valid @RequestBody OptimizeRouteRequest request) {
        return ResponseEntity.ok(routeOptimizationService.optimize(request));
    }

    @PostMapping
    public ResponseEntity<UserPlanResponse> create(
            @Valid @RequestBody CreateCustomPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userPlanService.create(request));
    }

    @PutMapping("/{planId}")
    public ResponseEntity<UserPlanResponse> update(
            @PathVariable Long planId, @Valid @RequestBody CreateCustomPlanRequest request) {
        return ResponseEntity.ok(userPlanService.update(planId, request));
    }

    @GetMapping("/{planId}")
    public ResponseEntity<UserPlanResponse> getById(@PathVariable Long planId) {
        return ResponseEntity.ok(userPlanService.getById(planId));
    }

    @GetMapping
    public ResponseEntity<List<UserPlanResponse>> getMyPlans() {
        return ResponseEntity.ok(userPlanService.getMyPlans());
    }

    @PostMapping("/{planId}/start")
    public ResponseEntity<UserPlanResponse> start(@PathVariable Long planId) {
        return ResponseEntity.ok(userPlanService.start(planId));
    }

    @DeleteMapping("/{planId}")
    public ResponseEntity<Void> delete(@PathVariable Long planId) {
        userPlanService.delete(planId);
        return ResponseEntity.noContent().build();
    }
}
