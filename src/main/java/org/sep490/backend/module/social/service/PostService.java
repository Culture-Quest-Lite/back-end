package org.sep490.backend.module.social.service;

import org.sep490.backend.module.social.dto.request.PostRequest;
import org.sep490.backend.module.social.dto.response.PostResponse;

public interface PostService {
    PostResponse createPost(PostRequest postRequest);

}
