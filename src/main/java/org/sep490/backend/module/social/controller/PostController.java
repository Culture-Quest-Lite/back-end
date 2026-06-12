package org.sep490.backend.module.social.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.social.dto.request.PostRequest;
import org.sep490.backend.module.social.dto.response.PostResponse;
import org.sep490.backend.module.social.service.PostService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<PostResponse> createPost(
            @Valid @RequestBody PostRequest request
    ) {
        PostResponse response = postService.createPost(request);
        return ResponseEntity.ok(response);
    }
}
