package org.sep490.backend.module.content.service.impl;

import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.content.dto.request.HandleReportReviewRequest;
import org.sep490.backend.module.content.dto.request.ReportReviewRequest;
import org.sep490.backend.module.content.dto.response.ReportReviewResponse;
import org.sep490.backend.module.content.service.inter.RatingSummaryService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.utils.SecurityUtils;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.dto.filter.ReviewFilterRequest;
import org.sep490.backend.module.content.dto.projection.ReviewRatingCountProjection;
import org.sep490.backend.module.content.dto.request.ReviewRequest;
import org.sep490.backend.module.content.dto.request.ReviewUpdateRequest;
import org.sep490.backend.module.content.dto.response.MediaResponse;
import org.sep490.backend.module.content.dto.response.ReviewResponse;
import org.sep490.backend.module.content.dto.response.ReviewSummaryResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Media;
import org.sep490.backend.module.content.entity.Review;
import org.sep490.backend.module.content.entity.ReviewAction;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.sep490.backend.module.content.entity.enumeration.MediaTargetType;
import org.sep490.backend.module.content.entity.enumeration.ReviewActionType;
import org.sep490.backend.module.content.entity.enumeration.ReviewStatus;
import org.sep490.backend.module.content.entity.enumeration.ReviewTargetType;
import org.sep490.backend.module.content.entity.enumeration.RouteStatus;
import org.sep490.backend.module.content.mapper.MediaMapper;
import org.sep490.backend.module.content.mapper.ReviewMapper;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.MediaRepository;
import org.sep490.backend.module.content.repository.ReviewActionRepository;
import org.sep490.backend.module.content.repository.ReviewRepository;
import org.sep490.backend.module.content.repository.RouteRepository;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.common.service.TransactionCompensationService;
import org.sep490.backend.module.content.service.inter.MediaService;
import org.sep490.backend.module.content.service.inter.ReviewService;
import org.sep490.backend.module.content.service.inter.S3Service;
import org.sep490.backend.module.content.specification.ReviewSpecification;
import org.sep490.backend.module.notification.entity.enumeration.NotificationType;
import org.sep490.backend.module.notification.service.NotificationService;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReviewServiceImpl implements ReviewService {

    ReviewRepository reviewRepository;
    ReviewActionRepository reviewActionRepository;
    ReviewMapper reviewMapper;
    MediaRepository mediaRepository;
    MediaMapper mediaMapper;
    MediaService mediaService;
    S3Service s3Service;
    TransactionCompensationService txCompensation;
    UserService userService;
    HotspotRepository hotspotRepository;
    RouteRepository routeRepository;
    StoryRepository storyRepository;
    RatingSummaryService ratingSummaryService;
    UserRepository userRepository;
    NotificationService notificationService;

    @Override
    @Transactional
    public ReviewResponse createReview(ReviewRequest reviewRequest) {
        User currentUser = userService.getCurrentUser();

        Review review = Review.builder()
                .user(currentUser)
                .rating(reviewRequest.getRating())
                .comment(reviewRequest.getComment())
                .status(ReviewStatus.ACTIVE)
                .build();

        attachTarget(review, reviewRequest.getTargetType(), reviewRequest.getTargetId());
        validateNotReviewedYet(currentUser.getUserId(), reviewRequest.getTargetType(), reviewRequest.getTargetId());

        review = reviewRepository.save(review);
        evictRatingCache(review);
        ReviewResponse response = toResponse(review, currentUser.getUserId());

        if (reviewRequest.getFiles() != null && reviewRequest.getFiles().length > 0) {
            try {
                List<MediaResponse> mediaResponses = mediaService.uploadAndSaveMedias(
                        reviewRequest.getFiles(), MediaTargetType.REVIEW, review.getReviewId());
                response.setMedias(mediaResponses);
            } catch (IOException e) {
                throw new BusinessException("Lỗi tải lên media: " + e.getMessage());
            }
        }
        return response;
    }

    @Override
    @Transactional
    public ReviewResponse updateReview(Long id, ReviewUpdateRequest reviewRequest) {
        User currentUser = userService.getCurrentUser();
        Review review = getReviewById(id);

        if (!review.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Bạn không có quyền chỉnh sửa đánh giá của người khác");
        }

        reviewMapper.updateFromRequest(review, reviewRequest);

        if (reviewRequest.getRemovedMediaIds() != null && !reviewRequest.getRemovedMediaIds().isEmpty()) {
            removeMedias(review, reviewRequest.getRemovedMediaIds());
        }

        review = reviewRepository.saveAndFlush(review);
        evictRatingCache(review);

        if (reviewRequest.getFiles() != null && reviewRequest.getFiles().length > 0) {
            try {
                mediaService.uploadAndSaveMedias(
                        reviewRequest.getFiles(), MediaTargetType.REVIEW, review.getReviewId());
            } catch (IOException e) {
                throw new BusinessException("Lỗi tải lên media: " + e.getMessage());
            }
        }

        ReviewResponse response = toResponse(review, currentUser.getUserId());
        response.setMedias(loadMedias(review.getReviewId()));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewResponse getReviewDetail(Long id) {
        return toResponseWithOwner(getReviewById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getAllReview(ReviewFilterRequest filter) {
        return search(filter);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getMyReviews(ReviewFilterRequest filter) {

        if(SecurityUtils.getCurrentUserKeyCloakId().isEmpty()) {
            throw new AccessDeniedException("Bạn cần đăng nhập để xem đánh giá của mình");
        }

        filter.setUserId(userService.getCurrentUser().getUserId());
        return search(filter);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewSummaryResponse getSummary(ReviewTargetType targetType, Long targetId) {
        if (targetType == null || targetId == null) {
            throw new BusinessException("Cần cung cấp loại đối tượng và ID đối tượng cần thống kê");
        }
        validateTargetExists(targetType, targetId);

        List<ReviewRatingCountProjection> counts = switch (targetType) {
            case HOTSPOT -> reviewRepository.countRatingsByHotspotId(targetId, ReviewStatus.ACTIVE);
            case ROUTE -> reviewRepository.countRatingsByRouteId(targetId, ReviewStatus.ACTIVE);
            case STORY -> reviewRepository.countRatingsByStoryId(targetId, ReviewStatus.ACTIVE);
        };

        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int star = 1; star <= 5; star++) {
            distribution.put(star, 0L);
        }

        long total = 0;
        long weightedSum = 0;
        for (ReviewRatingCountProjection count : counts) {
            distribution.put(count.getRating(), count.getTotal());
            total += count.getTotal();
            weightedSum += (long) count.getRating() * count.getTotal();
        }

        ReviewSummaryResponse response = new ReviewSummaryResponse();
        response.setTargetType(targetType);
        response.setTargetId(targetId);
        response.setTotalReviews(total);
        response.setAverageRating(total == 0 ? 0.0 : Math.round((double) weightedSum / total * 10) / 10.0);
        response.setRatingDistribution(distribution);
        return response;
    }

    @Override
    @Transactional
    public ReviewResponse updateReviewStatus(Long id, ReviewStatus status) {
        // Phân quyền do @PreAuthorize("hasAuthority('PERM_REVIEW_MODERATE')") ở controller đảm nhiệm.
        // KHÔNG hardcode UserRole ở đây: làm vậy sẽ vô hiệu hoá tính động — admin gỡ quyền
        // khỏi CURATOR trong ma trận nhưng if-check vẫn cho qua.
        Review review = getReviewById(id);
        review.setStatus(status);
        reviewRepository.save(review);
        evictRatingCache(review);
        return toResponseWithOwner(review);
    }

    @Override
    @Transactional
    public ReviewResponse toggleLikeReview(Long id) {
        User currentUser = userService.getCurrentUser();
        Review review = getReviewById(id);

        reviewActionRepository.findByReview_ReviewIdAndUser_UserIdAndActionType(
                        id, currentUser.getUserId(), ReviewActionType.LIKE)
                .ifPresentOrElse(existingLike -> {
                    Long likeActionId = existingLike.getReviewActionId();
                    review.getReviewActions().removeIf(action -> likeActionId.equals(action.getReviewActionId()));
                }, () -> review.getReviewActions().add(ReviewAction.builder()
                        .review(review)
                        .user(currentUser)
                        .actionType(ReviewActionType.LIKE)
                        .build()));

        reviewRepository.save(review);
        return toResponse(review, currentUser.getUserId());
    }

    @Override
    @Transactional
    public void deleteReview(Long id) {
        // Owner-or-permission do @perm.isOwnerOrHasPerm(#id,'REVIEW','REVIEW_DELETE_ANY') đảm nhiệm.
        Review review = getReviewById(id);

        review.setStatus(ReviewStatus.DELETED);
        reviewRepository.save(review);
        evictRatingCache(review);
    }

    @Override
    @Transactional(readOnly = true)
    public Review getReviewById(Long id) {
        return reviewRepository.findByReviewIdAndStatusNot(id, ReviewStatus.DELETED).orElseThrow(
                () -> new BusinessException("Không tìm thấy đánh giá với id: " + id)
        );
    }

    @Override
    @Transactional
    public ReportReviewResponse reportReview(Long reviewId, ReportReviewRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException("Đánh giá không tồn tại"));

        User currentUser = userService.getCurrentUser();

        // 1. Chống spam: Kiểm tra xem đã báo cáo chưa
        Optional<ReviewAction> existingReport = reviewActionRepository
                .findByReview_ReviewIdAndUser_UserIdAndActionType(reviewId, currentUser.getUserId(), ReviewActionType.REPORT);

        if (existingReport.isPresent() && !existingReport.get().getIsReportResolved()) {
            throw new BusinessException("Đánh giá này đã được bạn báo cáo trước đó");
        }

        // 2. Đếm số lượng báo cáo hiện tại
        Integer reportCount = reviewActionRepository.countByActionTypeAndReview_ReviewIdAndIsReportResolved(
                ReviewActionType.REPORT, reviewId, false);

        // 3. Đổi trạng thái nếu vượt ngưỡng 3 báo cáo
        if (reportCount + 1 >= 3) {
            review.setStatus(ReviewStatus.REPORTING);
            reviewRepository.save(review);
        }

        // 4. Lưu Action báo cáo
        ReviewAction reportAction = ReviewAction.builder()
                .review(review)
                .user(currentUser)
                .actionType(ReviewActionType.REPORT)
                .comment(request.getComment())
                .isReportResolved(false)
                .build();
        reportAction = reviewActionRepository.save(reportAction);

        // 5. Cảnh báo Admin nếu đạt 10 báo cáo
        if (reportCount + 1 == 10) {
            List<User> admins = userRepository.findByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE);
            if (!admins.isEmpty()) {
                notificationService.sendToMultipleUsers(
                        admins,
                        "Cảnh báo: Đánh giá bị báo cáo nhiều lần",
                        "Đánh giá #" + reviewId + " đã nhận được 10 lượt báo cáo. Vui lòng kiểm tra và xử lý.",
                        NotificationType.CONTENT, // Có thể đổi thành NotificationType.REVIEW nếu bạn có định nghĩa
                        reviewId
                );
            }
        }

        return toReportReviewResponse(reportAction);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportReviewResponse> getAllReportReviews() {
        User user = userService.getCurrentUser();

        if (user.getRole().equals(UserRole.EXPLORER)) {
            // User thường: Chỉ xem các báo cáo do chính mình tạo chưa được giải quyết
            return reviewActionRepository.findByActionTypeAndUser_UserIdAndIsReportResolved(
                            ReviewActionType.REPORT, user.getUserId(), false)
                    .stream()
                    .map(this::toReportReviewResponse)
                    .toList();
        } else if (user.getRole().equals(UserRole.ADMIN)) {
            // Admin: Xem toàn bộ báo cáo chưa giải quyết của hệ thống
            return reviewActionRepository.findByActionTypeAndIsReportResolved(ReviewActionType.REPORT, false)
                    .stream()
                    .map(this::toReportReviewResponse)
                    .sorted((r1, r2) -> r2.getReviewId().compareTo(r1.getReviewId()))
                    .toList();
        } else {
            throw new BusinessException("Người dùng không có quyền truy cập danh sách báo cáo");
        }
    }

    @Override
    @Transactional
    public ReviewResponse handleReport(Long reviewId, HandleReportReviewRequest request) {
        User admin = userService.getCurrentUser();
        if (!admin.getRole().equals(UserRole.ADMIN)) {
            throw new BusinessException("Chỉ Admin mới có quyền xử lý báo cáo");
        }

        Review currentReview = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException("Đánh giá không tồn tại"));

        if (currentReview.getStatus().equals(ReviewStatus.REPORTED)) {
            throw new BusinessException("Đánh giá này đã được xử lý trước đó");
        }

        List<ReviewAction> actions = reviewActionRepository.findAllById(request.getReviewActionIds());
        List<ReviewAction> savedActions = new ArrayList<>();

        for (ReviewAction action : actions) {
            if (!action.getReview().getReviewId().equals(currentReview.getReviewId())) {
                throw new BusinessException("Báo cáo #" + action.getReviewActionId() + " không thuộc về đánh giá này");
            }
            if (action.getIsReportResolved() != null && action.getIsReportResolved()) {
                throw new BusinessException("Báo cáo #" + action.getReviewActionId() + " đã được xử lý");
            }
            action.setIsReportResolved(true);
            savedActions.add(action);
        }

        String reason;
        if (request.getIsApproveReport()) {
            reason = "ADMIN approve report: " + request.getReason();
            currentReview.setStatus(ReviewStatus.REPORTED);
        } else {
            reason = "ADMIN reject report: " + request.getReason();
            currentReview.setStatus(ReviewStatus.ACTIVE);
        }

        ReviewAction resolvedAction = ReviewAction.builder()
                .review(currentReview)
                .user(admin)
                .actionType(ReviewActionType.RESOLVE_REPORT)
                .comment(reason)
                .build();
        savedActions.add(resolvedAction);

        reviewActionRepository.saveAll(savedActions);
        reviewRepository.save(currentReview);

        // 3. Thông báo cho người viết Review
        notificationService.sendAndSave(
                currentReview.getUser(),
                "Đánh giá của bạn đã bị xử lý",
                "Đánh giá của bạn đã được quản trị viên xem xét do: " + request.getReason(),
                NotificationType.CONTENT,
                reviewId
        );

        // 4. Thông báo cho những người đã report
        List<User> reporters = actions.stream().map(ReviewAction::getUser).distinct().toList();
        if (!reporters.isEmpty()) {
            notificationService.sendToMultipleUsers(
                    reporters,
                    "Kết quả báo cáo đánh giá",
                    "Cảm ơn bạn. Đánh giá #" + reviewId + " mà bạn báo cáo đã được hệ thống xử lý.",
                    NotificationType.CONTENT,
                    reviewId
            );
        }

        // Trả về DTO hiển thị Review (bạn cần có reviewMapper.toResponse)
        return reviewMapper.toResponse(currentReview);
    }

    // Hàm tiện ích map Entity sang Response DTO
    private ReportReviewResponse toReportReviewResponse(ReviewAction action) {
        return ReportReviewResponse.builder()
                .reviewActionId(action.getReviewActionId())
                .reviewId(action.getReview().getReviewId())
                .createdUserId(action.getUser().getUserId())
                .createdDisplayName(action.getUser().getDisplayName())
                .comment(action.getComment())
                .createdAt(action.getCreatedAt())
                .build();
    }

    private Page<ReviewResponse> search(ReviewFilterRequest filter) {
        Sort sort = filter.getSortDir().equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(filter.getSortBy()).ascending()
                : Sort.by(filter.getSortBy()).descending();

        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Specification<Review> spec = ReviewSpecification.filter(filter);

        Long currentUserId = getCurrentUserIdOrNull();
        Page<ReviewResponse> page = reviewRepository.findAll(spec, pageable)
                .map(review -> toResponseWithoutLikeInfo(review, currentUserId));

        applyLikeInfo(page.getContent(), currentUserId);
        return page;
    }

    private ReviewResponse toResponseWithoutLikeInfo(Review review, Long currentUserId) {
        ReviewResponse response = reviewMapper.toResponse(review);
        response.setIsOwner(currentUserId != null && currentUserId.equals(review.getUser().getUserId()));
        return response;
    }

    private ReviewResponse toResponseWithOwner(Review review) {
        return toResponse(review, getCurrentUserIdOrNull());
    }

    private ReviewResponse toResponse(Review review, Long currentUserId) {
        ReviewResponse response = reviewMapper.toResponse(review);
        response.setIsOwner(currentUserId != null && currentUserId.equals(review.getUser().getUserId()));

        Long reviewId = review.getReviewId();
        List<Long> ids = List.of(reviewId);
        response.setLikeCount(countLikes(ids).getOrDefault(reviewId, 0L));
        response.setIsLiked(currentUserId != null
                && findLikedIds(currentUserId, ids).contains(reviewId));
        return response;
    }

    private void applyLikeInfo(List<ReviewResponse> responses, Long currentUserId) {
        if (responses == null || responses.isEmpty()) {
            return;
        }
        List<Long> ids = responses.stream()
                .map(ReviewResponse::getReviewId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return;
        }

        Map<Long, Long> counts = countLikes(ids);
        Set<Long> likedIds = currentUserId == null ? Set.of() : findLikedIds(currentUserId, ids);

        responses.forEach(response -> {
            response.setLikeCount(counts.getOrDefault(response.getReviewId(), 0L));
            response.setIsLiked(likedIds.contains(response.getReviewId()));
        });
    }

    private Map<Long, Long> countLikes(List<Long> reviewIds) {
        Map<Long, Long> result = new java.util.HashMap<>();
        for (Object[] row : reviewActionRepository.countActionsByReviewIds(reviewIds, ReviewActionType.LIKE)) {
            result.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }

    private Set<Long> findLikedIds(Long userId, List<Long> reviewIds) {
        return reviewActionRepository.findLikedReviewIds(userId, reviewIds, ReviewActionType.LIKE);
    }

    private Long getCurrentUserIdOrNull() {
        if (SecurityUtils.getCurrentUserKeyCloakId().isEmpty()) {
            return null;
        }
        return userService.getCurrentUser().getUserId();
    }

    private List<MediaResponse> loadMedias(Long reviewId) {
        return mediaRepository.findByReview_ReviewIdOrderByDisplayOrderAsc(reviewId).stream()
                .map(mediaMapper::toResponse)
                .toList();
    }

    private void removeMedias(Review review, List<Long> removedMediaIds) {
        Set<Long> ownedMediaIds = review.getMedias().stream()
                .map(Media::getMediaId)
                .collect(Collectors.toSet());

        List<Long> notOwned = removedMediaIds.stream()
                .filter(mediaId -> !ownedMediaIds.contains(mediaId))
                .toList();
        if (!notOwned.isEmpty()) {
            throw new BusinessException("Media không thuộc đánh giá này: " + notOwned);
        }

        List<String> removedFileUrls = review.getMedias().stream()
                .filter(media -> removedMediaIds.contains(media.getMediaId()))
                .map(Media::getFileUrl)
                .filter(Objects::nonNull)
                .toList();

        review.getMedias().removeIf(media -> removedMediaIds.contains(media.getMediaId()));

        removedFileUrls.forEach(fileUrl -> txCompensation.runAfterCommit(
                "Xóa file media của đánh giá " + fileUrl,
                () -> s3Service.safeDeleteByUrl(fileUrl)));
    }

    private void attachTarget(Review review, ReviewTargetType targetType, Long targetId) {
        switch (targetType) {
            case HOTSPOT -> review.setHotspot(getActiveHotspot(targetId));
            case ROUTE -> review.setRoute(getPublishedRoute(targetId));
            case STORY -> review.setStory(getActiveStory(targetId));
        }
    }

    private void evictRatingCache(Review review) {
        if (review == null) {
            return;
        }
        if (review.getHotspot() != null) {
            ratingSummaryService.evict(ReviewTargetType.HOTSPOT, review.getHotspot().getHotspotId());
        } else if (review.getRoute() != null) {
            ratingSummaryService.evict(ReviewTargetType.ROUTE, review.getRoute().getRouteId());
        } else if (review.getStory() != null) {
            ratingSummaryService.evict(ReviewTargetType.STORY, review.getStory().getStoryId());
        }
    }

    private void validateTargetExists(ReviewTargetType targetType, Long targetId) {
        switch (targetType) {
            case HOTSPOT -> getActiveHotspot(targetId);
            case ROUTE -> getPublishedRoute(targetId);
            case STORY -> getActiveStory(targetId);
        }
    }

    private Hotspot getActiveHotspot(Long id) {
        Hotspot hotspot = hotspotRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Địa điểm không tồn tại với ID: " + id));
        if (hotspot.getStatus() == ContentStatus.DELETED) {
            throw new BusinessException("Địa điểm đã bị xóa, không thể đánh giá");
        }
        return hotspot;
    }

    private Route getPublishedRoute(Long id) {
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Lộ trình không tồn tại với ID: " + id));
        if (route.getStatus() == RouteStatus.DELETED) {
            throw new BusinessException("Lộ trình đã bị xóa, không thể đánh giá");
        }
        return route;
    }

    private Story getActiveStory(Long id) {
        Story story = storyRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Câu chuyện không tồn tại với ID: " + id));
        if (story.getStatus() == ContentStatus.DELETED) {
            throw new BusinessException("Câu chuyện đã bị xóa, không thể đánh giá");
        }
        return story;
    }

    private void validateNotReviewedYet(Long userId, ReviewTargetType targetType, Long targetId) {
        boolean reviewed = switch (targetType) {
            case HOTSPOT -> reviewRepository.existsByUser_UserIdAndHotspot_HotspotIdAndStatusNot(
                    userId, targetId, ReviewStatus.DELETED);
            case ROUTE -> reviewRepository.existsByUser_UserIdAndRoute_RouteIdAndStatusNot(
                    userId, targetId, ReviewStatus.DELETED);
            case STORY -> reviewRepository.existsByUser_UserIdAndStory_StoryIdAndStatusNot(
                    userId, targetId, ReviewStatus.DELETED);
        };
        if (reviewed) {
            throw new BusinessException("Bạn đã đánh giá đối tượng này rồi, vui lòng chỉnh sửa đánh giá cũ");
        }
    }
}
