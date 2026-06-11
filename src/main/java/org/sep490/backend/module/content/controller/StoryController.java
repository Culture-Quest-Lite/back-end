package org.sep490.backend.module.content.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.dto.request.StoryRequest;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.service.inter.StoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping
    public ResponseEntity<StoryResponse> create(@RequestBody StoryRequest storyRequest) {
        StoryResponse response = storyService.create(storyRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<StoryResponse> update(@PathVariable Long id, @RequestBody StoryRequest storyRequest) {
        StoryResponse response = storyService.update(id, storyRequest);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        storyService.delete(id);
        return ResponseEntity.ok("Deleted");
    }
}
