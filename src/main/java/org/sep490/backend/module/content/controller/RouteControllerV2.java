package org.sep490.backend.module.content.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.dto.request.RouteRequestV2;
import org.sep490.backend.module.content.dto.response.RouteResponse;
import org.sep490.backend.module.content.service.inter.RouteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/routes")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RouteControllerV2 {

    RouteService routeService;

    @PostMapping(,consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RouteResponse> createV2(@Valid @ModelAttribute RouteRequestV2 request) {
        RouteResponse routeResponse = routeService.createV2(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(routeResponse);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RouteResponse> updateV2(@PathVariable Long id, @Valid @RequestBody RouteRequestV2 routeRequest) {
        RouteResponse routeResponse = routeService.updateV2(id, routeRequest);
        return ResponseEntity.ok(routeResponse);
    }

}
