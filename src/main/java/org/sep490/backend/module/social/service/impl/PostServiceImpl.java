package org.sep490.backend.module.social.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.RouteRepository;
import org.sep490.backend.module.content.repository.TagRepository;
import org.sep490.backend.module.gamification.entity.PointTransaction;
import org.sep490.backend.module.gamification.entity.enumeration.TransactionType;
import org.sep490.backend.module.gamification.repository.PointTransactionRepository;
import org.sep490.backend.module.social.dto.request.DeletePostRequest;
import org.sep490.backend.module.social.dto.request.PostRequest;
import org.sep490.backend.module.social.dto.request.RejectPostRequest;
import org.sep490.backend.module.social.dto.request.UpdatePostRequest;
import org.sep490.backend.module.social.dto.response.PostResponse;
import org.sep490.backend.module.social.entity.Post;
import org.sep490.backend.module.social.entity.enumeration.PostStatus;
import org.sep490.backend.module.social.mapper.PostMapper;
import org.sep490.backend.module.social.repository.PostRepository;
import org.sep490.backend.module.social.service.PostService;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostServiceImpl implements PostService {
    PostRepository postRepository;
    HotspotRepository hotspotRepository;
    RouteRepository routeRepository;
    TagRepository tagRepository;
    PostMapper postMapper;
    UserService userService;
    PointTransactionRepository pointTransactionRepository;

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

        long balanceRemaining = user.getTotalPoints() + createPostPoints;
        user.setTotalPoints((int) balanceRemaining);

        PointTransaction pointTransaction = PointTransaction.builder()
                .user(user)
                .pointAmount(createPostPoints)
                .transactionType(TransactionType.EARN)
                .description("Bài viết của " + user.getUsername() + " đã được duyệt")
                .balanceRemaining(balanceRemaining)
                .referenceId(post.getPostId())
                .build();
        pointTransactionRepository.save(pointTransaction);
        return postMapper.toResponse(post);
    }

    @Override
    @Transactional(readOnly = true)
    public Slice<PostResponse> getPosts(PostStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Slice<Post> postSlice = postRepository.findByStatusOptional(status, pageable);
        return postSlice.map(postMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse getPostById(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Bài viết không tồn tại"));

        return postMapper.toResponse(post);
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
        return postMapper.toResponse(updatedPost);
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
        return newsfeedSlice.map(postMapper::toResponse);
    }

    @Override
    @Transactional
    public PostResponse approvePost(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Bài viết không tồn tại"));

        if (post.getStatus() != PostStatus.PENDING) {
            throw new BusinessException("Bài viết không ở trạng thại chờ phê duyệt");
        }

        // bổ sung logic cộng XP/điểm

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
    public Slice<PostResponse> getMyPosts(Pageable pageable) {
        User currentUser = userService.getCurrentUser();
        Slice<Post> posts = postRepository.findByUser_UserIdAndStatus(currentUser.getUserId(), PostStatus.APPROVED,
                pageable);
        return posts.map(postMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Slice<PostResponse> getPostsByUserId(Long userId, Pageable pageable) {
        Slice<Post> posts = postRepository.findByUser_UserIdAndStatus(userId, PostStatus.APPROVED, pageable);
        return posts.map(postMapper::toResponse);
    }
}
