package org.sep490.backend.module.gamification.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.user.dto.request.LevelRequest;
import org.sep490.backend.module.user.dto.response.LevelResponse;
import org.sep490.backend.module.user.dto.response.LevelProgressResponse;
import org.sep490.backend.module.user.service.LevelService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/gamification/levels")
@RequiredArgsConstructor
public class LevelController {

    private final LevelService levelService;

    @PostMapping
    public ResponseEntity<LevelResponse> createLevel(@Valid @RequestBody LevelRequest request) {
        LevelResponse response = levelService.createLevel(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<LevelResponse> updateLevel(
            @PathVariable("id") Long levelId,
            @Valid @RequestBody LevelRequest request) {
        LevelResponse response = levelService.updateLevel(levelId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLevel(@PathVariable("id") Long levelId) {
        levelService.deleteLevel(levelId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<LevelResponse>> getAllLevels() {
        List<LevelResponse> response = levelService.getAllLevels();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LevelResponse> getLevelById(@PathVariable("id") Long levelId) {
        LevelResponse response = levelService.getLevelById(levelId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/progress/me")
    public ResponseEntity<List<LevelProgressResponse>> getMyLevelProgress() {
        return ResponseEntity.ok(levelService.getMyLevelProgress());
    }

    @GetMapping("/progress/user/{userId}")
    public ResponseEntity<List<LevelProgressResponse>> getLevelProgressByUserId(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(levelService.getLevelProgressByUserId(userId));
    }
}
