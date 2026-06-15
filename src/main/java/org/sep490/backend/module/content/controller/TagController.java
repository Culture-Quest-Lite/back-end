package org.sep490.backend.module.content.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.dto.filter.TagFilterRequest;
import org.sep490.backend.module.content.dto.request.TagRequest;
import org.sep490.backend.module.content.dto.response.TagResponse;
import org.sep490.backend.module.content.service.inter.TagService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TagController {

    TagService tagService;

    @GetMapping
    public ResponseEntity<Page<TagResponse>> getAll(@Valid @ParameterObject @ModelAttribute TagFilterRequest filter) {
        return ResponseEntity.ok(tagService.getAllWithFilter(filter));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TagResponse> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(tagService.getDetail(id));
    }

    @PostMapping
    public ResponseEntity<TagResponse> create(@Valid @RequestBody TagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tagService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TagResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody TagRequest request
    ) {
        return ResponseEntity.ok(tagService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

