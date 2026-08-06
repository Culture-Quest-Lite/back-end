package org.sep490.backend.module.social.service.impl;

import org.sep490.backend.module.social.service.PostCounterService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.sep490.backend.module.content.dto.response.MediaResponse;
import org.sep490.backend.module.content.entity.Media;
import org.sep490.backend.module.content.entity.enumeration.MediaTargetType;
import org.sep490.backend.module.content.service.inter.MediaService;
import org.sep490.backend.module.content.service.inter.S3Service;
import org.springframework.beans.factory.annotation.Value;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.service.TransactionCompensationService;
import org.sep490.backend.common.utils.SecurityUtils;
import org.sep490.backend.module.admin.entity.enumeration.AuditAction;
import org.sep490.backend.module.admin.service.AuditLogService;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    S3Service s3Service;
    TransactionCompensationService txCompensation;
    AuditLogService auditLogService;
    PostCounterService postCounterService;

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

        applyTaggedHotspots(post, request.getHotspotIds());
        applyTaggedRoutes(post, request.getRouteIds());
        applyTags(post, request.getTagIds());

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
        // Endpoint này là public (xem PUBLIC_ENDPOINTS trong SecurityConfig) nên
        // KHÔNG được gọi getCurrentUser() — hàm đó ném RuntimeException khi chưa đăng nhập,
        // khiến toàn bộ API trả 500 cho khách vãng lai.
        Long currentUserId = findCurrentUserIdOrNull();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // Có status cụ thể thì dùng query lọc thêm visibility để khớp index
        // idx_post_feed_flow (status, visibility, created_at) -> Index Scan thay vì Seq Scan.
        Slice<Post> postSlice = status != null
                ? postRepository.findByStatusAndVisibility(status, PostVisibility.PUBLIC, pageable)
                : postRepository.findByStatusOptional(null, pageable);
        return postSlice.map(post -> toResponseWithLiked(post, currentUserId));
    }

    /**
     * Trả null khi chưa đăng nhập thay vì ném lỗi — dùng cho các endpoint public.
     *
     * KHÔNG bọc getCurrentUser() trong try/catch: hàm đó có @Transactional, nên khi nó
     * ném lỗi bên trong transaction cha thì transaction đã bị đánh dấu rollback-only.
     * Bắt được exception cũng vô ích — lúc commit vẫn nổ UnexpectedRollbackException.
     * Phải kiểm tra token TRƯỚC, chỉ gọi khi chắc chắn có người dùng.
     */
    private Long findCurrentUserIdOrNull() {
        if (SecurityUtils.getCurrentUserKeyCloakId().isEmpty()) {
            return null;
        }
        return userService.getCurrentUser().getUserId();
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse getPostById(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Bài viết không tồn tại"));

        // Public endpoint — xem ghi chú ở getPosts()
        return toResponseWithLiked(post, findCurrentUserIdOrNull());
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

        if (request.getHotspotIds() != null) {
            applyTaggedHotspots(post, request.getHotspotIds());
        }
        if (request.getRouteIds() != null) {
            applyTaggedRoutes(post, request.getRouteIds());
        }
        if (request.getTagIds() != null) {
            applyTags(post, request.getTagIds());
        }

        if (request.getRemovedMediaIds() != null && !request.getRemovedMediaIds().isEmpty()) {
            removeMedias(post, request.getRemovedMediaIds());
        }

        Post updatedPost = postRepository.saveAndFlush(post);

        if (request.getFiles() != null && request.getFiles().length > 0) {
            try {
                mediaService.uploadAndSaveMedias(
                        request.getFiles(), MediaTargetType.POST, updatedPost.getPostId());
            } catch (IOException e) {
                throw new BusinessException("Lỗi tải lên media: " + e.getMessage());
            }
        }

        return toResponseWithLiked(updatedPost, user.getUserId());
    }

    private void applyTaggedHotspots(Post post, List<Long> hotspotIds) {
        if (hotspotIds == null || hotspotIds.isEmpty()) {
            post.setTaggedHotspots(new HashSet<>());
            post.setIsTaggedHotspot(false);
            return;
        }

        List<Hotspot> hotspots = hotspotRepository.findAllById(hotspotIds);
        if (hotspots.size() != new HashSet<>(hotspotIds).size()) {
            throw new BusinessException("Địa điểm được gắn thẻ không tồn tại");
        }
        post.setTaggedHotspots(new HashSet<>(hotspots));
        post.setIsTaggedHotspot(true);
    }

    private void applyTaggedRoutes(Post post, List<Long> routeIds) {
        if (routeIds == null || routeIds.isEmpty()) {
            post.setTaggedRoutes(new HashSet<>());
            post.setIsTaggedRoute(false);
            return;
        }

        List<Route> routes = routeRepository.findAllById(routeIds);
        if (routes.size() != new HashSet<>(routeIds).size()) {
            throw new BusinessException("Tuyến đường được gắn thẻ không tồn tại");
        }
        post.setTaggedRoutes(new HashSet<>(routes));
        post.setIsTaggedRoute(true);
    }

    private void applyTags(Post post, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            post.setTags(new HashSet<>());
            return;
        }

        List<Tag> tags = tagRepository.findAllById(tagIds);
        if (tags.size() != new HashSet<>(tagIds).size()) {
            throw new BusinessException("Một số thẻ phân loại không tồn tại");
        }
        post.setTags(new HashSet<>(tags));
    }

    private void removeMedias(Post post, List<Long> removedMediaIds) {
        Set<Long> ownedMediaIds = post.getMedias().stream()
                .map(Media::getMediaId)
                .collect(Collectors.toSet());

        List<Long> notOwned = removedMediaIds.stream()
                .filter(mediaId -> !ownedMediaIds.contains(mediaId))
                .toList();
        if (!notOwned.isEmpty()) {
            throw new BusinessException("Media không thuộc bài viết này: " + notOwned);
        }

        List<String> removedFileUrls = post.getMedias().stream()
                .filter(media -> removedMediaIds.contains(media.getMediaId()))
                .map(Media::getFileUrl)
                .filter(Objects::nonNull)
                .toList();

        post.getMedias().removeIf(media -> removedMediaIds.contains(media.getMediaId()));

        removedFileUrls.forEach(fileUrl -> txCompensation.runAfterCommit(
                "Xóa file media của bài viết " + fileUrl,
                () -> s3Service.safeDeleteByUrl(fileUrl)));
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

        // Tách làm 2 query thay vì ORDER BY CASE WHEN:
        // biểu thức trong ORDER BY khiến index không dùng được -> Seq Scan toàn bảng
        // (7.7 giây ở trang 1000 với 1.22 triệu bài). Hai query dưới đều dùng được
        // index idx_post_feed_flow (status, visibility, created_at).
        List<Long> followingIds = postRepository.findFollowingIds(currentUser.getUserId());

        int offset = page * size;
        int need = offset + size + 1;   // +1 để biết còn trang sau không
        Pageable limit = PageRequest.of(0, need);

        List<Post> merged = new ArrayList<>(need);

        // Phần 1: bài của người đang theo dõi (ưu tiên hiển thị trước)
        if (!followingIds.isEmpty()) {
            merged.addAll(postRepository.findFeedByAuthors(
                    PostStatus.APPROVED, PostVisibility.PUBLIC, followingIds, null, limit));
        }

        // Phần 2: bù cho đủ bằng bài của những người còn lại
        if (merged.size() < need) {
            Pageable remaining = PageRequest.of(0, need - merged.size());
            // NOT IN với danh sách rỗng là lỗi cú pháp SQL, nên truyền một id không tồn tại
            List<Long> excluded = followingIds.isEmpty() ? List.of(-1L) : followingIds;
            merged.addAll(postRepository.findFeedExcludingAuthors(
                    PostStatus.APPROVED, PostVisibility.PUBLIC, excluded, null, remaining));
        }

        boolean hasNext = merged.size() > offset + size;
        List<PostResponse> content = merged.stream()
                .skip(offset)
                .limit(size)
                .map(post -> toResponseWithLiked(post, currentUser.getUserId()))
                .toList();

        return new SliceImpl<>(content, PageRequest.of(page, size), hasNext);
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

        PostStatus oldStatus = post.getStatus();
        post.setStatus(PostStatus.DELETED);
        post.setReason(request.getReason());
        Post savedPost = postRepository.save(post);

        auditLogService.log(AuditAction.BAN_POST, "posts", String.valueOf(id),
                Map.of("status", oldStatus),
                Map.of("status", PostStatus.DELETED, "reason", String.valueOf(request.getReason())));

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
        } else {
            PostAction likeAction = PostAction.builder()
                    .post(post)
                    .user(currentUser)
                    .actionType(PostActionType.LIKE)
                    .build();
            post.getPostActions().add(likeAction);
        }
        postRepository.save(post);
        postCounterService.evict(id);

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
        postCounterService.evict(id);
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
        // shareCount tăng trên post GỐC (post), không phải bài chia sẻ mới tạo bên dưới
        postCounterService.evict(id);

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
        postCounterService.apply(response, post.getPostId());
        response.setIsLiked(isLikedBy(post.getPostId(), currentUserId));

        if (response.getSharedPost() != null && post.getSharedPost() != null) {
            Long sharedPostId = post.getSharedPost().getPostId();
            postCounterService.apply(response.getSharedPost(), sharedPostId);
            response.getSharedPost().setIsLiked(isLikedBy(sharedPostId, currentUserId));
        }
        return response;
    }

    /**
     * Dùng EXISTS query thay vì stream cả collection postActions:
     * cách cũ nạp toàn bộ like của post vào bộ nhớ chỉ để tìm một user.
     */
    private boolean isLikedBy(Long postId, Long userId) {
        if (postId == null || userId == null) {
            return false;
        }
        return postActionRepository.existsByPost_PostIdAndUser_UserIdAndActionType(
                postId, userId, PostActionType.LIKE);
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
