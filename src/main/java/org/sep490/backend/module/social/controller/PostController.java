package org.sep490.backend.module.social.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.social.dto.request.*;
import org.sep490.backend.module.social.dto.response.PostResponse;
import org.sep490.backend.module.social.dto.response.ReportPostResponse;
import org.sep490.backend.module.social.entity.enumeration.PostStatus;
import org.sep490.backend.module.social.service.PostService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.sep490.backend.module.social.dto.response.CommentResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERM_POST_CREATE')")
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

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@perm.isOwnerOrHasPerm(#id, 'POST', 'POST_UPDATE_ANY')")
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable Long id,
            @Valid @ModelAttribute UpdatePostRequest request
    ) {
        return ResponseEntity.ok(postService.updatePost(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.isOwnerOrHasPerm(#id, 'POST', 'POST_DELETE_ANY')")
    public ResponseEntity<String> deletePost(@PathVariable Long id) {
        postService.deletePost(id);
        return ResponseEntity.ok("Deleted post successfully!");
    }

    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("@perm.isOwnerOrHasPerm(#id, 'POST', 'POST_DELETE_ANY')")
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
            direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) PostStatus status
    ) {
        Slice<PostResponse> responses = postService.getMyPosts(pageable, status);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/hotspot/{hotspotId}")
    public ResponseEntity<Slice<PostResponse>> getPostsByHotspotId(
            @PathVariable Long hotspotId,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt",
            direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Slice<PostResponse> responses = postService.getPostsByHotspotId(hotspotId, pageable);
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<PostResponse> toggleLikePost(@PathVariable Long id) {
        return ResponseEntity.ok(postService.toggleLikePost(id));
    }

    @PostMapping("/{id}/comment")
    public ResponseEntity<PostResponse> commentPost(
            @PathVariable Long id,
            @Valid @RequestBody CommentRequest request
    ) {
        PostResponse response = postService.commentPost(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/share")
    public ResponseEntity<PostResponse> sharePost(
            @PathVariable Long id,
            @RequestBody @Valid ShareRequest request
    ) {
        PostResponse response = postService.sharePost(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<Slice<CommentResponse>> getCommentsByPostId(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Slice<CommentResponse> responses = postService.getCommentsByPostId(id, page, size);
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{id}/reports")
    public ResponseEntity<ReportPostResponse> reportPost(
            @PathVariable Long id,
            @Valid @RequestBody ReportPostRequest request
    ) {
        ReportPostResponse response = postService.reportPost(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reports")
    public ResponseEntity<List<ReportPostResponse>> getReports() {
        return ResponseEntity.ok(postService.getAllReportPosts());
    }

    @PutMapping("/{id}/reports")
    public ResponseEntity<PostResponse> handleReport(
            @PathVariable("id") Long postId,
            @Valid @RequestBody HandleReportPostRequest request
    ) {
        PostResponse response = postService.handleReport(postId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/approve-pending")
    public ResponseEntity<PostResponse> approvePendingPost(
            @PathVariable("id") Long postId,
            @Valid @RequestParam PostStatus status
    ) {
        return ResponseEntity.ok(postService.approvePendingPost(postId, status));
    }

    @PutMapping("/{id}/restore")
    public ResponseEntity<PostResponse> restorePost(
            @PathVariable("id") Long postId
    ) {
        return ResponseEntity.ok(postService.restorePost(postId));
    }
}
