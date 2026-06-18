package org.sep490.backend.module.exploration.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.exploration.dto.response.SavedRouteResponse;
import org.sep490.backend.module.exploration.service.inter.SavedRouteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/saved-routes")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SavedRouteController {

    SavedRouteService savedRouteService;

    @PostMapping("/save/{id}")
    public ResponseEntity<SavedRouteResponse> saveRoute(@PathVariable("id") Long routeId) {
        SavedRouteResponse savedRouteResponse = savedRouteService.saveRoute(routeId);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedRouteResponse);
    }

    @DeleteMapping("/un-save/{id}")
    public ResponseEntity<String> deleteRoute(@PathVariable("id") Long savedRouteId) {
        savedRouteService.unsaveRoute(savedRouteId);
        return ResponseEntity.ok("Route deleted");
    }

    @GetMapping
    public ResponseEntity<List<SavedRouteResponse>> getAllRoutes() {
        List<SavedRouteResponse> savedRoutes = savedRouteService.findAll();
        return ResponseEntity.ok(savedRoutes);
    }
}
