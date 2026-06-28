package org.sep490.backend.module.content.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.filter.dto.SearchRequest;
import org.sep490.backend.module.content.dto.request.RouteRequest;
import org.sep490.backend.module.content.dto.response.RouteResponse;
import org.sep490.backend.module.content.service.inter.RouteService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RouteController {

    RouteService routeService;

    @GetMapping("/{id}")
    public ResponseEntity<RouteResponse> getById(@PathVariable Long id) {
        RouteResponse routeResponse = routeService.getDetail(id);
        return ResponseEntity.ok(routeResponse);
    }

    @GetMapping("/search")
    public ResponseEntity<Page<RouteResponse>> filterRoutes(@ModelAttribute SearchRequest request) {
        Page<RouteResponse> routeResponse = routeService.filterRoutes(request);
        return ResponseEntity.ok(routeResponse);
    }

    @GetMapping("/hotspot/{hotspotId}")
    public ResponseEntity<List<RouteResponse>> getByHotspotId(@PathVariable Long hotspotId) {
        List<RouteResponse> routeResponses = routeService.getByHotspotId(hotspotId);
        return ResponseEntity.ok(routeResponses);
    }

    @PostMapping
    public ResponseEntity<RouteResponse> create(@Valid @RequestBody RouteRequest routeRequest) {
        RouteResponse routeResponse = routeService.create(routeRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(routeResponse);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RouteResponse> update(@PathVariable Long id, @Valid @RequestBody RouteRequest routeRequest) {
        RouteResponse routeResponse = routeService.update(id, routeRequest);
        return ResponseEntity.ok(routeResponse);
    }

    @PostMapping("/{routeId}/add/{hotspotId}")
    public ResponseEntity<RouteResponse> add(@PathVariable Long routeId, @PathVariable Long hotspotId) {
        RouteResponse routeResponse = routeService.addHotspotToEndOfRoute(routeId, hotspotId);
        return ResponseEntity.ok(routeResponse);
    }

    @DeleteMapping("/{routeId}/remove/{hotspotId}")
    public ResponseEntity<RouteResponse> remove(@PathVariable Long routeId, @PathVariable Long hotspotId) {
        RouteResponse routeResponse = routeService.removeHotspotFromRoute(routeId, hotspotId);
        return ResponseEntity.ok(routeResponse);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        routeService.delete(id);
        return ResponseEntity.ok("Route deleted successfully");
    }
}
