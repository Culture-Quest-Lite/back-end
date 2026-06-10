package org.sep490.backend.module.content.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.dto.request.HotspotRequest;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.springframework.http.HttpStatus;
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

    @PostMapping
    public ResponseEntity<HotspotResponse> createHotspot(@RequestBody HotspotRequest hotspotRequest) {
        HotspotResponse response = hotspotService.create(hotspotRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<HotspotResponse> updateHotspot(@PathVariable Long id, @RequestBody HotspotRequest hotspotRequest) {
        HotspotResponse response = hotspotService.update(id, hotspotRequest);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteHotspot(@PathVariable Long id) {
        hotspotService.delete(id);
        return ResponseEntity.ok("Hotspot deleted successfully");
    }
}
