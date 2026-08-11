package org.sep490.backend.module.social.service.impl;

import jakarta.ws.rs.POST;
import org.apache.tomcat.util.buf.ByteChunk;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.notification.entity.enumeration.NotificationType;
import org.sep490.backend.module.notification.service.NotificationService;
import org.sep490.backend.module.social.dto.request.*;
import org.sep490.backend.module.social.dto.response.ReportPostResponse;
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
import org.sep490.backend.module.user.entity.UserFollow;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.sep490.backend.module.user.repository.UserFollowRepository;
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
import org.sep490.backend.module.social.entity.enumeration.PostVisibility;
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
    UserRepository userRepository;
    PostMapper postMapper;
    UserService userService;
    RewardTransactionService rewardTransactionService;
    MediaService mediaService;
    S3Service s3Service;
    TransactionCompensationService txCompensation;
    AuditLogService auditLogService;
    PostCounterService postCounterService;
    NotificationService notificationService;
    UserFollowRepository userFollowRepository;

    @NonFinal
    @Value("${app.points.create-post:20}")
    long createPostPoints;

    @Override
    @Transactional
    public PostResponse createPost(PostRequest request) {
        User user = userService.getCurrentUser();

        Post post = postMapper.toEntity(request);
        post.setUser(user);

        if(request.getVisibility().equals(PostVisibility.PUBLIC)) {
            post.setStatus(PostStatus.PENDING);
        } else {
            post.setStatus(PostStatus.APPROVED);
        }

        applyTaggedHotspots(post, request.getHotspotIds());
        applyTaggedRoutes(post, request.getRouteIds());
        applyTags(post, request.getTagIds());

        post = postRepository.saveAndFlush(post);

        PostResponse response = toResponseWithLiked(post, user.getUserId());
        response.setPointEarned((int) createPostPoints);
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
        Long currentUserId = findCurrentUserIdOrNull();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Slice<Post> postSlice = status != null
                ? postRepository.findByStatusAndVisibility(status, PostVisibility.PUBLIC, pageable)
                : postRepository.findByStatusOptional(null, pageable);
        return postSlice.map(post -> toResponseWithLiked(post, currentUserId));
    }

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
            if (post.getStatus().equals(PostStatus.PENDING)) {
                if(!request.getVisibility().equals(PostVisibility.PUBLIC)) {
                    post.setStatus(PostStatus.APPROVED);
                }
            } else if (post.getStatus().equals(PostStatus.APPROVED)) {
                if (request.getVisibility().equals(PostVisibility.PUBLIC) && !post.getVisibility().equals(PostVisibility.PUBLIC)) {
                    post.setStatus(PostStatus.PENDING);
                }
            } else if (post.getStatus().equals(PostStatus.REJECTED) || post.getStatus().equals(PostStatus.REPORTED)) {
                if (request.getVisibility().equals(PostVisibility.PUBLIC)) {
                    post.setStatus(PostStatus.PENDING);
                } else {
                    post.setStatus(PostStatus.APPROVED);
                }
            }
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

        PostStatus status = post.getStatus();

        switch (status) {
            case APPROVED:
                post.setStatus(PostStatus.DELETED);
                postRepository.save(post);
                break;
            case REPORTING:
                throw new BusinessException("Không thể xóa bài viết đang trong quá trình xử lý báo cáo vi phạm");
            default:
                postRepository.delete(post);
        }
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
        Long currentUserId = findCurrentUserIdOrNull();

        int offset = page * size;
        int need = offset + size + 1;
        Pageable limit = PageRequest.of(0, need);

        List<Post> merged = new ArrayList<>(need);

        if (currentUserId == null) { // guest
            merged.addAll(postRepository.findByStatusAndVisibility(
                    PostStatus.APPROVED, PostVisibility.PUBLIC, limit).getContent());
        } else {
            List<Long> followingIds = postRepository.findFollowingIds(currentUserId);
            List<Long> mutualFriendIds = userFollowRepository.findMutualFollowers(currentUserId)
                    .stream().map(User::getUserId).toList();

            List<Long> safeMutualFriendIds = mutualFriendIds.isEmpty() ? List.of(-1L) : mutualFriendIds;

            if (!followingIds.isEmpty()) {
                merged.addAll(postRepository.findFeedByAuthorsWithFriends(
                        PostStatus.APPROVED, followingIds, safeMutualFriendIds, null, limit));
            }

            if (merged.size() < need) {
                Pageable remaining = PageRequest.of(0, need - merged.size());
                List<Long> excluded = followingIds.isEmpty() ? List.of(-1L) : followingIds;
                merged.addAll(postRepository.findFeedExcludingAuthors(
                        PostStatus.APPROVED, PostVisibility.PUBLIC, excluded, null, remaining));
            }
        }

        boolean hasNext = merged.size() > offset + size;
        List<PostResponse> content = merged.stream()
                .skip(offset)
                .limit(size)
                .map(post -> toResponseWithLiked(post, currentUserId))
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

        if (post.getStatus() == PostStatus.DELETED) {
            throw new BusinessException("Bài viết không tồn tại hoặc đã bị xóa");
        }

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
    public Slice<PostResponse> getPostsByUserId(Long targetUserId, Pageable pageable) {
        Long currentUserId = findCurrentUserIdOrNull();
        List<PostVisibility> allowedVisibilities;

        if (currentUserId == null) { // guest view
            allowedVisibilities = List.of(PostVisibility.PUBLIC);
        } else if (currentUserId.equals(targetUserId)) { // owner view
            allowedVisibilities = List.of(PostVisibility.PUBLIC, PostVisibility.FRIENDS, PostVisibility.PRIVATE);
        } else {
            User currentUser = userService.getUserById(currentUserId);
            User targetUser = userRepository.findById(targetUserId)
                    .orElseThrow(() -> new BusinessException("Người dùng không tồn tại"));

            boolean isMutual = userFollowRepository.existsByFollowerAndFollowing(currentUser, targetUser) &&
                    userFollowRepository.existsByFollowerAndFollowing(targetUser, currentUser);

            if (isMutual) { // friend view
                allowedVisibilities = List.of(PostVisibility.PUBLIC, PostVisibility.FRIENDS);
            } else { // non-friend view
                allowedVisibilities = List.of(PostVisibility.PUBLIC);
            }
        }

        Slice<Post> posts = postRepository.findByUser_UserIdAndStatusAndVisibilityIn(
                targetUserId, PostStatus.APPROVED, allowedVisibilities, pageable);

        return posts.map(post -> toResponseWithLiked(post, currentUserId));
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

        boolean isLiked = false;
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
            isLiked = true;
        }

        postRepository.save(post);
        postCounterService.evict(id);

        if (isLiked) {
            notifyPostInteraction(currentUser, post);
        }

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

        notifyPostInteraction(currentUser, post);

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
        postCounterService.evict(id);

        Post sharedPost = Post.builder()
                .user(currentUser)
                .content(request.getContent())
                .visibility(request.getVisibility() != null ? request.getVisibility() : PostVisibility.PUBLIC)
                .sharedPost(post)
                .status(PostStatus.APPROVED)
                .build();

        Post savedPost = postRepository.save(sharedPost);

        notifyPostInteraction(currentUser, post);

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

    @Override
    @Transactional
    public ReportPostResponse reportPost(Long id, ReportPostRequest request) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Bài viết không tồn tại"));
        User currentUser = userService.getCurrentUser();

        Optional<PostAction> existingReport = postActionRepository.findByPost_PostIdAndUser_UserIdAndActionType(
                id, currentUser.getUserId(), PostActionType.REPORT);

        if (existingReport.isPresent() && !existingReport.get().getIsReportResolved()) {
            throw new BusinessException("Bài viết này đã được bạn báo cáo trước đó");
        }

        Integer reportCount = postActionRepository.countByActionTypeAndPost_PostIdAndIsReportResolved(
                PostActionType.REPORT, id, false);

        if (reportCount + 1 >= 3) {
            post.setStatus(PostStatus.REPORTING);
            postRepository.save(post);
        }

        PostAction reportAction = PostAction.builder()
                .post(post)
                .user(currentUser)
                .actionType(PostActionType.REPORT)
                .comment(request.getComment())
                .isReportResolved(false)
                .build();
        reportAction = postActionRepository.save(reportAction);

        // noti admin at 10 pending REPORT action
        if (reportCount + 1 == 10) {
            List<User> admins = userRepository.findByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE);
            if (!admins.isEmpty()) {
                notificationService.sendToMultipleUsers(
                        admins,
                        "Cảnh báo bài viết bị báo cáo nhiều",
                        "Bài viết #" + id + " đã nhận được 10 lượt báo cáo. Vui lòng kiểm tra và xử lý.",
                        NotificationType.POST,
                        id
                );
            }
        }

        return toReportPostResponse(reportAction);
    }

    @Override
    public List<ReportPostResponse> getAllReportPosts() {

        User user = userService.getCurrentUser();

        if(user.getRole().equals(UserRole.EXPLORER)) {
            return postActionRepository.findByActionTypeAndUser_UserIdAndIsReportResolved(PostActionType.REPORT, user.getUserId(), false)
                    .stream()
                    .map(this::toReportPostResponse)
                    .toList();
        } else if (user.getRole().equals(UserRole.ADMIN)) {
            return postActionRepository.findByActionTypeAndIsReportResolved(PostActionType.REPORT, false)
                    .stream()
                    .map(this::toReportPostResponse)
                    .sorted((r1, r2) -> r2.getPostId().compareTo(r1.getPostId()))
                    .toList();
        } else {
            throw new BusinessException("Người dùng không có quyền truy cập danh sách báo cáo bài viết");
        }
    }

    @Override
    public PostResponse handleReport(Long postId, HandleReportPostRequest request) {

        User admin = userService.getCurrentUser();

        if(!admin.getRole().equals(UserRole.ADMIN)) {
            throw new BusinessException("Người dùng không có quyền xử lý báo cáo bài viết");
        }

        List<PostAction> actions = postActionRepository.findAllById(request.getPostActionIds());
        List<PostAction> savedActions = new ArrayList<>();
        Post currentPost = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("Bài viết không tồn tại"));

        if(currentPost.getStatus().equals(PostStatus.REPORTED)) {
            throw new BusinessException("Bài viết đã bị báo cáo và xử lý trước đó");
        }

        for(PostAction action : actions) {
            if(!action.getPost().equals(currentPost)) {
                throw new BusinessException("Báo cáo #" + action.getPostActionId() + " không thuộc bài viết này");
            }

            if(action.getIsReportResolved() != null && action.getIsReportResolved()) {
                throw new BusinessException("Báo cáo bài viết đã được xử lý trước đó");
            }

            action.setIsReportResolved(true);
            savedActions.add(action);
        }

        String reason;

        if(request.getIsApproveReport()) {
            reason = "ADMIN approve report: " + request.getReason();
            currentPost.setStatus(PostStatus.REPORTED);
        } else {
            reason = "ADMIN reject report: " + request.getReason();
            currentPost.setStatus(PostStatus.APPROVED);
        }

        // create latest action for post
        PostAction resolvedAction = PostAction.builder()
                .post(currentPost)
                .user(admin)
                .actionType(PostActionType.RESOLVE_REPORT)
                .comment(reason)
                .build();


        savedActions.add(resolvedAction);

        postActionRepository.saveAll(savedActions);
        postRepository.save(currentPost);

        notificationService.sendAndSave(
                currentPost.getUser(),
                "Bài viết bị xử lý",
                "Bài viết của bạn đã bị admin xử lý với lý do: " + request.getReason(),
                NotificationType.POST,
                postId);

        List<User> reporters = actions.stream().map(PostAction::getUser).distinct().toList();
        notificationService.sendToMultipleUsers(
                reporters,
                "Kết quả báo cáo",
                "Báo cáo của bạn về bài viết #" + postId + " đã được admin xử lý.",
                NotificationType.POST,
                postId);

        return PostResponse.builder()
                .postId(currentPost.getPostId())
                .content(currentPost.getContent())
                .visibility(currentPost.getVisibility())
                .status(currentPost.getStatus())
                .createdAt(currentPost.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public PostResponse approvePendingPost(Long postId, PostStatus status) {
        User user = userService.getCurrentUser();

        if(!user.getRole().equals(UserRole.ADMIN)) {
            throw new BusinessException("Người dùng không có quyền cập nhật trạng thái bài viết");
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("Bài viết không tồn tại"));

        if(!post.getStatus().equals(PostStatus.PENDING)) {
            throw new BusinessException("Chỉ có thể cập nhật trạng thái bài viết đang chờ phê duyệt");
        }

        post.setStatus(status);
        postRepository.save(post);

        if(status.equals(PostStatus.APPROVED)) {
            List<User> followers = userFollowRepository.findAllByFollowing(post.getUser()).stream()
                    .map(UserFollow::getFollower).toList();

            if (!followers.isEmpty()) {
                notificationService.sendToMultipleUsers(
                        followers,
                        "Bài viết mới",
                        post.getUser().getDisplayName() + " vừa đăng một bài viết mới.",
                        NotificationType.POST,
                        post.getPostId()
                );
            }
        }

        return toResponseWithLiked(post, post.getUser().getUserId());
    }

    @Override
    @Transactional
    public PostResponse restorePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("Bài viết không tồn tại"));

        if(!post.getStatus().equals(PostStatus.DELETED)) {
            throw new BusinessException("Chỉ có thể khôi phục bài viết đã bị xóa");
        }

        User user = userService.getCurrentUser();

        if(!post.getUser().equals(user)) {
            throw new BusinessException("Bạn không có quyền khôi phục bài viết này");
        }

        post.setStatus(PostStatus.APPROVED);
        post = postRepository.save(post);

        return postMapper.toResponse(post);
    }

    @Override
    @Transactional
    public void deleteDeletedPosts() {
        LocalDateTime deletedTime = LocalDateTime.now().minusDays(30);
        List<Post> deletedPosts = postRepository.findByStatusAndUpdatedAtBefore(PostStatus.DELETED, deletedTime);
        for (Post post : deletedPosts) {
            postRepository.delete(post);
        }
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

    private ReportPostResponse toReportPostResponse(PostAction action) {
        return ReportPostResponse.builder()
                .postActionId(action.getPostActionId())
                .postId(action.getPost().getPostId())
                .createdUserId(action.getUser().getUserId())
                .createdDisplayName(action.getUser().getDisplayName())
                .comment(action.getComment())
                .createdAt(action.getCreatedAt())
                .build();
    }

    private void notifyPostInteraction(User sender, Post post) {
        long totalInteractions = postActionRepository.countActionsByPostId(post.getPostId()).stream()
                .filter(row -> {
                    PostActionType type = (PostActionType) row[0];
                    return type == PostActionType.LIKE || type == PostActionType.COMMENT || type == PostActionType.SHARE;
                })
                .mapToLong(row -> ((Number) row[1]).longValue())
                .sum();

        notificationService.sendOrUpdateInteractionNotification(sender, post.getUser(), post.getPostId(), totalInteractions);
    }
}
