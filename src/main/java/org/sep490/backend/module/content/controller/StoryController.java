package org.sep490.backend.module.content.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.dto.filter.StoryFilterRequest;
import org.sep490.backend.module.content.dto.request.StoryRequest;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.sep490.backend.module.content.service.inter.StoryService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stories")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StoryController {

    StoryService storyService;

    @GetMapping("/{id}")
    public ResponseEntity<StoryResponse> getDetails(@PathVariable Long id) {
        StoryResponse response = storyService.getDetail(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<StoryResponse>> search(@ModelAttribute StoryFilterRequest filter) {
        Page<StoryResponse> response = storyService.getAll(filter);
        return ResponseEntity.ok(response);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoryResponse> create(@Valid @ModelAttribute StoryRequest storyRequest) {
        StoryResponse response = storyService.create(storyRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoryResponse> update(@PathVariable Long id, @Valid @ModelAttribute StoryRequest storyRequest) {
        StoryResponse response = storyService.update(id, storyRequest);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<StoryResponse> updateStatus(@PathVariable Long id, @RequestParam ContentStatus status) {
        StoryResponse response = storyService.updateStatus(id, status);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        storyService.delete(id);
        return ResponseEntity.ok("Deleted");
    }

    @GetMapping("/hotspot/{hotspotId}")
    public ResponseEntity<List<StoryResponse>> getByHotspot(
            @PathVariable Long hotspotId,
            @RequestParam(required = false) Long routeId) {
        List<StoryResponse> response = storyService.getByHotspot(hotspotId, routeId);
        return ResponseEntity.ok(response);
    }
}
