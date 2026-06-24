package org.sep490.backend.module.social.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.social.dto.request.PostRequest;
import org.sep490.backend.module.social.dto.request.UpdatePostRequest;
import org.sep490.backend.module.social.dto.response.PostResponse;
import org.sep490.backend.module.social.entity.enumeration.PostStatus;
import org.sep490.backend.module.social.service.PostService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> createPost(
            @Valid @ModelAttribute PostRequest request
    ) {
        PostResponse response = postService.createPost(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Slice<PostResponse>> getPosts(
            @RequestParam(required = false) PostStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Slice<PostResponse> response = postService.getPosts(status, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPostById(@PathVariable Long id) {
        return ResponseEntity.ok(postService.getPostById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePostRequest request
    ) {
        return ResponseEntity.ok(postService.updatePost(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deletePost(@PathVariable Long id) {
        postService.deletePost(id);
        return ResponseEntity.ok("Deleted post successfully!");
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<String> deletePostPermanently(@PathVariable Long id) {
        postService.deletePostPermanently(id);
        return ResponseEntity.ok("Deleted post permanent successfully!");
    }

    @GetMapping("/newsfeed")
    public ResponseEntity<Slice<PostResponse>> getNewsfeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Slice<PostResponse> responses = postService.getNewsfeed(page, size);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/my-posts")
    public ResponseEntity<Slice<PostResponse>> getMyPosts(
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt",
            direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Slice<PostResponse> responses = postService.getMyPosts(pageable);
        return ResponseEntity.ok(responses);
    }
}
