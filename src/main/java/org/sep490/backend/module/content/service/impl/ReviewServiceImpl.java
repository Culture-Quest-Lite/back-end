package org.sep490.backend.module.content.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
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
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.sep490.backend.module.content.entity.enumeration.MediaTargetType;
import org.sep490.backend.module.content.entity.enumeration.ReviewStatus;
import org.sep490.backend.module.content.entity.enumeration.ReviewTargetType;
import org.sep490.backend.module.content.entity.enumeration.RouteStatus;
import org.sep490.backend.module.content.mapper.MediaMapper;
import org.sep490.backend.module.content.mapper.ReviewMapper;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.MediaRepository;
import org.sep490.backend.module.content.repository.ReviewRepository;
import org.sep490.backend.module.content.repository.RouteRepository;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.common.service.TransactionCompensationService;
import org.sep490.backend.module.content.service.inter.MediaService;
import org.sep490.backend.module.content.service.inter.ReviewService;
import org.sep490.backend.module.content.service.inter.S3Service;
import org.sep490.backend.module.content.specification.ReviewSpecification;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReviewServiceImpl implements ReviewService {

    ReviewRepository reviewRepository;
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
        ReviewResponse response = reviewMapper.toResponse(review);
        response.setIsOwner(true);

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
            throw new BusinessException("Bạn không có quyền chỉnh sửa đánh giá của người khác");
        }

        reviewMapper.updateFromRequest(review, reviewRequest);

        if (reviewRequest.getRemovedMediaIds() != null && !reviewRequest.getRemovedMediaIds().isEmpty()) {
            removeMedias(review, reviewRequest.getRemovedMediaIds());
        }

        review = reviewRepository.saveAndFlush(review);

        if (reviewRequest.getFiles() != null && reviewRequest.getFiles().length > 0) {
            try {
                mediaService.uploadAndSaveMedias(
                        reviewRequest.getFiles(), MediaTargetType.REVIEW, review.getReviewId());
            } catch (IOException e) {
                throw new BusinessException("Lỗi tải lên media: " + e.getMessage());
            }
        }

        ReviewResponse response = reviewMapper.toResponse(review);
        response.setIsOwner(true);
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
        User currentUser = userService.getCurrentUser();
        if (currentUser.getRole() != UserRole.ADMIN && currentUser.getRole() != UserRole.CURATOR) {
            throw new BusinessException("Bạn không có quyền kiểm duyệt đánh giá");
        }

        Review review = getReviewById(id);
        review.setStatus(status);
        reviewRepository.save(review);

        return toResponseWithOwner(review);
    }

    @Override
    @Transactional
    public void deleteReview(Long id) {
        User currentUser = userService.getCurrentUser();
        Review review = getReviewById(id);

        boolean isOwner = review.getUser().getUserId().equals(currentUser.getUserId());
        if (!isOwner && currentUser.getRole() != UserRole.ADMIN) {
            throw new BusinessException("Bạn không có quyền xóa đánh giá này!");
        }

        review.setStatus(ReviewStatus.DELETED);
        reviewRepository.save(review);
    }

    @Override
    @Transactional(readOnly = true)
    public Review getReviewById(Long id) {
        return reviewRepository.findByReviewIdAndStatusNot(id, ReviewStatus.DELETED).orElseThrow(
                () -> new BusinessException("Không tìm thấy đánh giá với id: " + id)
        );
    }

    private Page<ReviewResponse> search(ReviewFilterRequest filter) {
        Sort sort = filter.getSortDir().equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(filter.getSortBy()).ascending()
                : Sort.by(filter.getSortBy()).descending();

        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Specification<Review> spec = ReviewSpecification.filter(filter);

        Long currentUserId = getCurrentUserIdOrNull();
        return reviewRepository.findAll(spec, pageable)
                .map(review -> toResponse(review, currentUserId));
    }

    private ReviewResponse toResponseWithOwner(Review review) {
        return toResponse(review, getCurrentUserIdOrNull());
    }

    private ReviewResponse toResponse(Review review, Long currentUserId) {
        ReviewResponse response = reviewMapper.toResponse(review);
        response.setIsOwner(currentUserId != null && currentUserId.equals(review.getUser().getUserId()));
        return response;
    }

    private Long getCurrentUserIdOrNull() {
        try {
            return userService.getCurrentUser().getUserId();
        } catch (RuntimeException e) {
            return null;
        }
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

        // Gom file_url trước khi orphanRemoval xóa row, nếu không sẽ mất dấu file trên S3
        List<String> removedFileUrls = review.getMedias().stream()
                .filter(media -> removedMediaIds.contains(media.getMediaId()))
                .map(Media::getFileUrl)
                .filter(Objects::nonNull)
                .toList();

        review.getMedias().removeIf(media -> removedMediaIds.contains(media.getMediaId()));

        // Chỉ xóa file sau khi commit — nếu rollback thì row vẫn còn và đang trỏ tới nó
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
