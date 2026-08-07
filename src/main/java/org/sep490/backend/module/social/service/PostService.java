package org.sep490.backend.module.social.service;

import jakarta.validation.Valid;
import org.sep490.backend.module.social.dto.request.*;
import org.sep490.backend.module.social.dto.response.PostResponse;
import org.sep490.backend.module.social.dto.response.ReportPostResponse;
import org.sep490.backend.module.social.entity.enumeration.PostStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import org.sep490.backend.module.social.dto.response.CommentResponse;

import java.util.List;

public interface PostService {
    PostResponse createPost(PostRequest postRequest);
    Slice<PostResponse> getPosts(PostStatus status, int page, int size);
    PostResponse getPostById(Long id);
    PostResponse updatePost(Long id, UpdatePostRequest updatePostRequest);
    void deletePost(Long id);
    void deletePostPermanently(Long id);
    Slice<PostResponse> getNewsfeed(int page, int size);
    PostResponse approvePost(Long id);
    PostResponse rejectPost(Long id, RejectPostRequest request);
    PostResponse banPostByAdmin(Long id, DeletePostRequest request);
    Slice<PostResponse> getMyPosts(Pageable pageable, PostStatus status);
    Slice<PostResponse> getPostsByUserId(Long userId, Pageable pageable);
    Slice<PostResponse> getPostsByHotspotId(Long hotspotId, Pageable pageable);
    PostResponse toggleLikePost(Long id);
    PostResponse commentPost(Long id, CommentRequest request);
    PostResponse sharePost(Long id, ShareRequest request);
    Slice<CommentResponse> getCommentsByPostId(Long id, int page, int size);
    ReportPostResponse reportPost(Long id, ReportPostRequest request);
    List<ReportPostResponse> getAllReportPosts();
    PostResponse handleReport(Long postActionId, List<HandleReportPostRequest> requests);
}
