package org.sep490.backend.module.social.service;

import org.sep490.backend.module.social.dto.request.PostRequest;
import org.sep490.backend.module.social.dto.request.RejectPostRequest;
import org.sep490.backend.module.social.dto.request.UpdatePostRequest;
import org.sep490.backend.module.social.dto.response.PostResponse;
import org.sep490.backend.module.social.entity.enumeration.PostStatus;
import org.springframework.data.domain.Slice;

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
}
