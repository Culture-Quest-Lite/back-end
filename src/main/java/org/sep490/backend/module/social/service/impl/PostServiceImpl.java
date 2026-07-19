package org.sep490.backend.module.social.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.sep490.backend.module.content.dto.response.MediaResponse;
import org.sep490.backend.module.content.entity.enumeration.MediaTargetType;
import org.sep490.backend.module.content.service.inter.MediaService;
import org.springframework.beans.factory.annotation.Value;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.RouteRepository;
import org.sep490.backend.module.content.repository.TagRepository;
import org.sep490.backend.module.gamification.entity.enumeration.TransactionType;
import org.sep490.backend.module.gamification.service.RewardTransactionService;
import org.sep490.backend.module.gamification.dto.request.RewardTransactionRequest;
import org.sep490.backend.module.social.dto.request.CommentRequest;
import org.sep490.backend.module.social.dto.request.DeletePostRequest;
import org.sep490.backend.module.social.dto.request.PostRequest;
import org.sep490.backend.module.social.dto.request.ShareRequest;
import org.sep490.backend.module.social.entity.enumeration.PostVisibility;
import org.sep490.backend.module.social.dto.request.RejectPostRequest;
import org.sep490.backend.module.social.dto.request.UpdatePostRequest;
import org.sep490.backend.module.social.dto.response.CommentResponse;
import org.sep490.backend.module.social.dto.response.PostResponse;
import org.sep490.backend.module.social.entity.PostAction;
import org.sep490.backend.module.social.entity.enumeration.PostActionType;
import org.sep490.backend.module.social.repository.PostActionRepository;
import org.sep490.backend.module.social.entity.Post;
import org.sep490.backend.module.social.entity.enumeration.PostStatus;
import org.sep490.backend.module.social.mapper.PostMapper;
import org.sep490.backend.module.social.repository.PostRepository;
import org.sep490.backend.module.social.service.PostService;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostServiceImpl implements PostService {
    PostRepository postRepository;
    PostActionRepository postActionRepository;
    HotspotRepository hotspotRepository;
    RouteRepository routeRepository;
    TagRepository tagRepository;
    PostMapper postMapper;
    UserService userService;
    RewardTransactionService rewardTransactionService;
    MediaService mediaService;

    @NonFinal
    @Value("${app.points.create-post:20}")
    long createPostPoints;

    @Override
    @Transactional
    public PostResponse createPost(PostRequest request) {
        User user = userService.getCurrentUser();

        Post post = postMapper.toEntity(request);
        post.setUser(user);
        post.setStatus(PostStatus.PENDING);

        if (request.getHotspotIds() != null && !request.getHotspotIds().isEmpty()) {
            List<Hotspot> hotspots = hotspotRepository.findAllById(request.getHotspotIds());
            if (hotspots.size() != request.getHotspotIds().size()) {
                throw new BusinessException("Một số địa điểm được tag không tồn tại");
            }
            post.setTaggedHotspots(new HashSet<>(hotspots));
            post.setIsTaggedHotspot(true);
        } else {
            post.setIsTaggedHotspot(false);
        }

        if (request.getRouteIds() != null && !request.getRouteIds().isEmpty()) {
            List<Route> routes = routeRepository.findAllById(request.getRouteIds());
            if (routes.size() != request.getRouteIds().size()) {
                throw new BusinessException("Một số tuyến đường được tag không tồn tại");
            }
            post.setTaggedRoutes(new HashSet<>(routes));
            post.setIsTaggedRoute(true);
        } else {
            post.setIsTaggedRoute(false);
        }

        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            List<Tag> tags = tagRepository.findAllById(request.getTagIds());
            if (tags.size() != request.getTagIds().size()) {
                throw new BusinessException("Một số thẻ phân loại không tồn tại");
            }
            post.setTags(new HashSet<>(tags));
        }

        post = postRepository.saveAndFlush(post);

        PostResponse response = toResponseWithLiked(post, user.getUserId());
        if (request.getFiles() != null && request.getFiles().length > 0) {
            try {
                List<MediaResponse> mediaResponses = mediaService.uploadAndSaveMedias(
                        request.getFiles(), MediaTargetType.POST, post.getPostId());
                response.setMedias(mediaResponses);
            } catch (IOException e) {
                throw new BusinessException("Lỗi tải lên media: " + e.getMessage());
            }
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Slice<PostResponse> getPosts(PostStatus status, int page, int size) {
        User currentUser = userService.getCurrentUser();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Slice<Post> postSlice = postRepository.findByStatusOptional(status, pageable);
        return postSlice.map(post -> toResponseWithLiked(post, currentUser.getUserId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse getPostById(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Bài viết không tồn tại"));

        User currentUser = userService.getCurrentUser();
        return toResponseWithLiked(post, currentUser.getUserId());
    }

    @Override
    @Transactional
    public PostResponse updatePost(Long id, UpdatePostRequest request) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Bài viết không tồn tại"));

        User user = userService.getCurrentUser();
        if (!post.getUser().getUserId().equals(user.getUserId())) {
            throw new BusinessException("Bạn không có quyền chỉnh sửa bài viết của người khác");
        }

        post.setContent(request.getContent());
        if (request.getVisibility() != null) {
            post.setVisibility(request.getVisibility());
        }

        post.getMedias().clear();
        if (request.getMedias() != null && !request.getMedias().isEmpty()) {
            post.getMedias().addAll(request.getMedias());
        }

        Post updatedPost = postRepository.save(post);
        return toResponseWithLiked(updatedPost, user.getUserId());
    }

    @Override
    @Transactional
    public void deletePost(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Bài viết không tồn tại với ID: " + id));

        User currentUser = userService.getCurrentUser();
        if (!post.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new BusinessException("Bạn không có quyền xóa bài viết này!");
        }

        post.setStatus(PostStatus.DELETED);
        postRepository.save(post);
    }

    @Override
    @Transactional
    public void deletePostPermanently(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Bài viết không tồn tại với ID: " + id));

        User currentUser = userService.getCurrentUser();
        if (!post.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new BusinessException("Bạn không có quyền xóa bài viết này!");
        }

        postRepository.delete(post);
    }

    @Override
    @Transactional
    public Slice<PostResponse> getNewsfeed(int page, int size) {
        User currentUser = userService.getCurrentUser();
        Pageable pageable = PageRequest.of(page, size);
        PostStatus status = PostStatus.APPROVED;
        Slice<Post> newsfeedSlice = postRepository.findNewsfeed(currentUser, status, pageable);
        return newsfeedSlice.map(post -> toResponseWithLiked(post, currentUser.getUserId()));
    }

    @Override
    @Transactional
    public PostResponse approvePost(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Bài viết không tồn tại"));

        if (post.getStatus() != PostStatus.PENDING) {
            throw new BusinessException("Bài viết không ở trạng thại chờ phê duyệt");
        }

        RewardTransactionRequest rewardRequest = RewardTransactionRequest.builder()
                .userId(post.getUser().getUserId())
                .pointsAmount(createPostPoints)
                .xpAmount(0L)
                .transactionType(TransactionType.POST_CREATION)
                .description("Bài viết của " + post.getUser().getUsername() + " đã được duyệt")
                .referenceId(post.getPostId())
                .build();
        rewardTransactionService.createRewardTransaction(rewardRequest);

        User currentUser = userService.getCurrentUser();
        post.setModerateBy(currentUser.getUserId());
        post.setModerateAt(LocalDateTime.now());
        post.setStatus(PostStatus.APPROVED);
        Post savedPost = postRepository.save(post);
        return postMapper.toResponse(savedPost);
    }

    @Override
    @Transactional
    public PostResponse rejectPost(Long id, RejectPostRequest request) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Bài viết không tồn tại"));

        if (post.getStatus() != PostStatus.PENDING) {
            throw new BusinessException("Bài viết không ở trạng thái chờ phê duyệt");
        }

        User currentUser = userService.getCurrentUser();
        post.setModerateBy(currentUser.getUserId());
        post.setModerateAt(LocalDateTime.now());
        post.setReason(request.getRejectReason());
        post.setStatus(PostStatus.REJECTED);
        Post savedPost = postRepository.save(post);
        return postMapper.toResponse(savedPost);
    }

    @Override
    @Transactional
    public PostResponse banPostByAdmin(Long id, DeletePostRequest request) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Bài viết không tồn tại hoặc đã bị xóa"));

        post.setStatus(PostStatus.DELETED);
        post.setReason(request.getReason());
        Post savedPost = postRepository.save(post);
        return postMapper.toResponse(savedPost);
    }

    @Override
    @Transactional(readOnly = true)
    public Slice<PostResponse> getMyPosts(Pageable pageable, PostStatus status) {
        User currentUser = userService.getCurrentUser();
        Slice<Post> posts = postRepository.findByUser_UserIdAndStatus(currentUser.getUserId(), status,
                pageable);
        return posts.map(post -> toResponseWithLiked(post, currentUser.getUserId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Slice<PostResponse> getPostsByUserId(Long userId, Pageable pageable) {
        User currentUser = userService.getCurrentUser();
        Slice<Post> posts = postRepository.findByUser_UserIdAndStatus(userId, PostStatus.APPROVED, pageable);
        return posts.map(post -> toResponseWithLiked(post, currentUser.getUserId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Slice<PostResponse> getPostsByHotspotId(Long hotspotId, Pageable pageable) {
        if (!hotspotRepository.existsById(hotspotId)) {
            throw new BusinessException("Địa điểm không tồn tại với ID: " + hotspotId);
        }
        User currentUser = userService.getCurrentUser();
        Slice<Post> posts = postRepository.findByHotspotIdAndStatus(hotspotId, PostStatus.APPROVED, pageable);
        return posts.map(post -> toResponseWithLiked(post, currentUser.getUserId()));
    }

    @Override
    @Transactional
    public PostResponse toggleLikePost(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Bài viết không tồn tại"));
        User currentUser = userService.getCurrentUser();

        Optional<PostAction> existingLike = postActionRepository.findByPost_PostIdAndUser_UserIdAndActionType(
                id, currentUser.getUserId(), PostActionType.LIKE);

        if (existingLike.isPresent()) {
            Long likeActionId = existingLike.get().getPostActionId();
            post.getPostActions().removeIf(action -> likeActionId.equals(action.getPostActionId()));
            post.setIsLiked(false);
        } else {
            PostAction likeAction = PostAction.builder()
                    .post(post)
                    .user(currentUser)
                    .actionType(PostActionType.LIKE)
                    .build();
            post.getPostActions().add(likeAction);
            post.setIsLiked(true);
        }
        postRepository.save(post);

        return toResponseWithLiked(post, currentUser.getUserId());
    }

    @Override
    @Transactional
    public PostResponse commentPost(Long id, CommentRequest request) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Bài viết không tồn tại"));
        User currentUser = userService.getCurrentUser();

        PostAction.PostActionBuilder commentActionBuilder = PostAction.builder()
                .post(post)
                .user(currentUser)
                .actionType(PostActionType.COMMENT)
                .comment(request.getComment());

        if (request.getParentActionId() != null) {
            PostAction parentAction = postActionRepository.findById(request.getParentActionId())
                    .orElseThrow(() -> new BusinessException("Bình luận gốc không tồn tại"));
            if (parentAction.getActionType() != PostActionType.COMMENT) {
                throw new BusinessException("Chỉ có thể phản hồi lại một bình luận");
            }
            commentActionBuilder.parentAction(parentAction);
        }

        postActionRepository.save(commentActionBuilder.build());
        post = postRepository.findById(id).orElse(post);
        return toResponseWithLiked(post, currentUser.getUserId());
    }

    @Override
    @Transactional
    public PostResponse sharePost(Long id, ShareRequest request) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Bài viết không tồn tại"));
        if (post.getStatus() != PostStatus.APPROVED) {
            throw new BusinessException("Chỉ có thể chia sẻ bài viết đã được phê duyệt");
        }

        User currentUser = userService.getCurrentUser();

        PostAction shareAction = PostAction.builder()
                .post(post)
                .user(currentUser)
                .actionType(PostActionType.SHARE)
                .build();
        postActionRepository.save(shareAction);

        Post sharedPost = Post.builder()
                .user(currentUser)
                .content(request.getContent())
                .visibility(request.getVisibility() != null ? request.getVisibility() : PostVisibility.PUBLIC)
                .sharedPost(post)
                .status(PostStatus.APPROVED)
                .build();

        Post savedPost = postRepository.save(sharedPost);
        return toResponseWithLiked(savedPost, currentUser.getUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public Slice<CommentResponse> getCommentsByPostId(Long id, int page, int size) {
        if (!postRepository.existsById(id)) {
            throw new BusinessException("Bài viết không tồn tại");
        }

        Pageable pageable = PageRequest.of(page, size);
        Slice<PostAction> rootComments = postActionRepository
                .findByPost_PostIdAndActionTypeAndParentActionIsNullOrderByCreatedAtAsc(id, PostActionType.COMMENT, pageable);

        return rootComments.map(this::mapToCommentResponse);
    }

    private PostResponse toResponseWithLiked(Post post, Long currentUserId) {
        PostResponse response = postMapper.toResponse(post);
        response.setIsLiked(isLikedBy(post, currentUserId));
        if (response.getSharedPost() != null && post.getSharedPost() != null) {
            response.getSharedPost().setIsLiked(isLikedBy(post.getSharedPost(), currentUserId));
        }
        return response;
    }

    private boolean isLikedBy(Post post, Long userId) {
        return post.getPostActions() != null && post.getPostActions().stream()
                .anyMatch(a -> a.getActionType() == PostActionType.LIKE
                        && a.getUser().getUserId().equals(userId));
    }

    private CommentResponse mapToCommentResponse(PostAction action) {
        if (action == null) return null;

        List<CommentResponse> childReplies = List.of();
        if (action.getReplies() != null) {
            childReplies = action.getReplies().stream()
                    .map(this::mapToCommentResponse)
                    .toList();
        }

        return CommentResponse.builder()
                .postActionId(action.getPostActionId())
                .postId(action.getPost().getPostId())
                .userId(action.getUser().getUserId())
                .username(action.getUser().getUsername())
                .displayName(action.getUser().getDisplayName())
                .comment(action.getComment())
                .createdAt(action.getCreatedAt())
                .replies(childReplies)
                .build();
    }
}
