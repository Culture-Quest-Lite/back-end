package org.sep490.backend.module.admin.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.admin.dto.filter.SubscriptionPlanFilterRequest;
import org.sep490.backend.module.admin.dto.request.SubscriptionPlanRequest;
import org.sep490.backend.module.admin.dto.response.SubscriptionPlanResponse;
import org.sep490.backend.module.admin.service.SubscriptionPlanService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/subscription-plans")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SubscriptionPlanController {

    SubscriptionPlanService subscriptionPlanService;

    @GetMapping
    public ResponseEntity<Page<SubscriptionPlanResponse>> getAll(
            @Valid @ParameterObject @ModelAttribute SubscriptionPlanFilterRequest filter) {
        return ResponseEntity.ok(subscriptionPlanService.getAllWithFilter(filter));
    }

    @PostMapping
    public ResponseEntity<SubscriptionPlanResponse> create(
            @Valid @RequestBody SubscriptionPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriptionPlanService.createSubscriptionPlan(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SubscriptionPlanResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody SubscriptionPlanRequest request) {
        return ResponseEntity.ok(subscriptionPlanService.updateSubscriptionPlan(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        subscriptionPlanService.deleteSubscriptionPlan(id);
        return ResponseEntity.noContent().build();
    }
}
