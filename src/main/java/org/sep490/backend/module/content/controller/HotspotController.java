package org.sep490.backend.module.content.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.springframework.data.domain.Page;
import org.sep490.backend.common.filter.dto.SearchRequest;
import org.sep490.backend.module.content.dto.request.HotspotRequest;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/hotspots")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class HotspotController {

    HotspotService hotspotService;

    @GetMapping
    public ResponseEntity<List<HotspotResponse>> getHotspots() {
        List<HotspotResponse> responses = hotspotService.getAll();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<HotspotResponse> getHotspotById(@PathVariable Long id) {
        return ResponseEntity.ok(hotspotService.getDetail(id));
    }

    @GetMapping("/nearby")
    public ResponseEntity<List<HotspotResponse>> getNearbyHotspots(
            @RequestParam(value = "latitude") Double latitude,
            @RequestParam(value = "longitude") Double longitude,
            @RequestParam(value = "distance", defaultValue = "1000") Double distance) {

        List<HotspotResponse> responses = hotspotService.getNearbyHotspots(latitude, longitude, distance);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/routes/{routeId}")
    public ResponseEntity<List<HotspotResponse>> getHotspotsByRouteId(@PathVariable("routeId") Long id) {
        List<HotspotResponse> responses = hotspotService.getHotspotsByRouteId(id);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/search")
    public ResponseEntity<Page<HotspotResponse>> filter(@ModelAttribute SearchRequest request) {
        Page<HotspotResponse> responses = hotspotService.filterHotspots(request);
        return ResponseEntity.ok(responses);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<HotspotResponse> createHotspot(@Valid @ModelAttribute HotspotRequest hotspotRequest) {
        HotspotResponse response = hotspotService.create(hotspotRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<HotspotResponse> updateHotspot(@PathVariable Long id, @Valid @RequestBody HotspotRequest hotspotRequest) {
        HotspotResponse response = hotspotService.update(id, hotspotRequest);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<HotspotResponse> updateHotspotStatus(@PathVariable Long id, @Valid @RequestParam ContentStatus status) {
        HotspotResponse response = hotspotService.updateStatus(id, status);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteHotspot(@PathVariable Long id) {
        hotspotService.delete(id);
        return ResponseEntity.ok("Hotspot deleted successfully");
    }
}
