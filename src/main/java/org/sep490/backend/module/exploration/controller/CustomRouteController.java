package org.sep490.backend.module.exploration.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.dto.request.FinalizeCustomRouteRequest;
import org.sep490.backend.module.content.dto.response.RouteResponse;
import org.sep490.backend.module.content.entity.enumeration.RouteStatus;
import org.sep490.backend.module.content.service.inter.RouteService;
import org.sep490.backend.module.exploration.service.inter.CustomRouteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/custom-routes")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CustomRouteController {

    CustomRouteService customRouteService;

    @PostMapping("/record")
    public ResponseEntity<RouteResponse> recordJourney() {
        return ResponseEntity.ok(customRouteService.recordJourney());
    }

    @PutMapping("/record/finish")
    public ResponseEntity<RouteResponse> finishRecordJourney() {
        return ResponseEntity.ok(customRouteService.finishRecordJourney());
    }

    @PutMapping("/record/finalize")
    public ResponseEntity<RouteResponse> finalizeRecordJourney(@RequestBody FinalizeCustomRouteRequest request) {
        return ResponseEntity.ok(customRouteService.finalizeCustomRoute(request));
    }

    @GetMapping("/my-journey")
    @Operation(summary = "Get Explorer's Journey", description = "RECORDING, DRAFT, TRIAL")
    public ResponseEntity<List<RouteResponse>> getMyJourney(@RequestParam(required = false) RouteStatus routeStatus) {
        return ResponseEntity.ok(customRouteService.getMyJourney(routeStatus));
    }
}
