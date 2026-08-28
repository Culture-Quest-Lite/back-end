package org.sep490.backend.module.content.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.service.TransactionCompensationService;
import org.sep490.backend.common.utils.SecurityUtils;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.dto.projection.ReviewRatingCountProjection;
import org.sep490.backend.module.content.dto.request.ReviewRequest;
import org.sep490.backend.module.content.dto.request.ReviewUpdateRequest;
import org.sep490.backend.module.content.dto.response.ReviewResponse;
import org.sep490.backend.module.content.dto.response.ReviewSummaryResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Media;
import org.sep490.backend.module.content.entity.Review;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
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
import org.sep490.backend.module.content.service.inter.MediaService;
import org.sep490.backend.module.content.service.inter.RatingSummaryService;
import org.sep490.backend.module.content.service.inter.S3Service;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho ĐÁNH GIÁ (Review) trên Hotspot / Route / Story.
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceImplTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ReviewActionRepository reviewActionRepository;
    @Mock private ReviewMapper reviewMapper;
    @Mock private MediaRepository mediaRepository;
    @Mock private MediaMapper mediaMapper;
    @Mock private MediaService mediaService;
    @Mock private S3Service s3Service;
    @Mock private TransactionCompensationService txCompensation;
    @Mock private UserService userService;
    @Mock private HotspotRepository hotspotRepository;
    @Mock private RouteRepository routeRepository;
    @Mock private StoryRepository storyRepository;
    @Mock private RatingSummaryService ratingSummaryService;

    @InjectMocks private ReviewServiceImpl reviewService;

    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserKeyCloakId)
                .thenReturn(Optional.of("kc-001"));
        when(reviewMapper.toResponse(any(Review.class))).thenAnswer(inv -> {
            ReviewResponse response = new ReviewResponse();
            response.setReviewId(((Review) inv.getArgument(0)).getReviewId());
            return response;
        });
        when(reviewActionRepository.countActionsByReviewIds(anyList(), any()))
                .thenReturn(List.of());
        when(reviewActionRepository.findLikedReviewIds(anyLong(), anyList(), any()))
                .thenReturn(Set.of());
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    private static User user(Long userId) {
        User user = new User();
        user.setUserId(userId);
        user.setUsername("traveler01");
        user.setDisplayName("Tran Minh Anh");
        user.setEmail("traveler01@gmail.com");
        return user;
    }

    private static Hotspot hotspot(Long id, ContentStatus status) {
        Hotspot hotspot = new Hotspot();
        hotspot.setHotspotId(id);
        hotspot.setHotspotName("Ho Guom");
        hotspot.setStatus(status);
        return hotspot;
    }

    private static Route route(Long id, RouteStatus status) {
        Route route = new Route();
        route.setRouteId(id);
        route.setRouteName("Pho co Ha Noi");
        route.setStatus(status);
        return route;
    }

    private static Story story(Long id, ContentStatus status) {
        Story story = new Story();
        story.setStoryId(id);
        story.setTitle("Su tich Ho Guom");
        story.setStatus(status);
        return story;
    }

    private static ReviewRequest reviewRequest(ReviewTargetType targetType, Long targetId,
                                               Integer rating) {
        ReviewRequest request = new ReviewRequest();
        request.setTargetType(targetType);
        request.setTargetId(targetId);
        request.setRating(rating);
        request.setComment("Cảnh đẹp, nhân viên thân thiện");
        return request;
    }

    // =====================================================================
    // Function: createReview
    // =====================================================================
    @Nested
    @DisplayName("createReview")
    class CreateReviewTest {

        // UTCID01 - Abnormal: đánh giá địa điểm không tồn tại
        @Test
        void createReview_hotspotNotFound_throwsHotspotNotFound() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(hotspotRepository.findById(10L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.createReview(
                            reviewRequest(ReviewTargetType.HOTSPOT, 10L, 5)));

            assertEquals("Địa điểm không tồn tại với ID: 10", ex.getMessage());
            verify(reviewRepository, never()).save(any());
        }

        // UTCID02 - Abnormal: địa điểm đã bị xóa
        @Test
        void createReview_deletedHotspot_throwsCannotReview() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(hotspotRepository.findById(10L))
                    .thenReturn(Optional.of(hotspot(10L, ContentStatus.DELETED)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.createReview(
                            reviewRequest(ReviewTargetType.HOTSPOT, 10L, 5)));

            assertEquals("Địa điểm đã bị xóa, không thể đánh giá", ex.getMessage());
        }

        // UTCID03 - Abnormal: lộ trình đã bị xóa
        @Test
        void createReview_deletedRoute_throwsCannotReview() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(routeRepository.findById(20L))
                    .thenReturn(Optional.of(route(20L, RouteStatus.DELETED)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.createReview(
                            reviewRequest(ReviewTargetType.ROUTE, 20L, 4)));

            assertEquals("Lộ trình đã bị xóa, không thể đánh giá", ex.getMessage());
        }

        // UTCID04 - Abnormal: câu chuyện không tồn tại
        @Test
        void createReview_storyNotFound_throwsStoryNotFound() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(storyRepository.findById(30L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.createReview(
                            reviewRequest(ReviewTargetType.STORY, 30L, 3)));

            assertEquals("Câu chuyện không tồn tại với ID: 30", ex.getMessage());
        }

        // UTCID05 - Abnormal: người dùng đã đánh giá đối tượng này rồi
        @Test
        void createReview_alreadyReviewed_throwsDuplicateReview() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(hotspotRepository.findById(10L))
                    .thenReturn(Optional.of(hotspot(10L, ContentStatus.PUBLISHED)));
            when(reviewRepository.existsByUser_UserIdAndHotspot_HotspotIdAndStatusNot(
                    1L, 10L, ReviewStatus.DELETED)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.createReview(
                            reviewRequest(ReviewTargetType.HOTSPOT, 10L, 5)));

            assertEquals("Bạn đã đánh giá đối tượng này rồi, vui lòng chỉnh sửa đánh giá cũ",
                    ex.getMessage());
            verify(reviewRepository, never()).save(any());
        }

        // UTCID06 - Normal: đánh giá địa điểm thành công -> status ACTIVE, xóa cache điểm TB
        @Test
        void createReview_validHotspot_createsActiveReviewAndEvictsCache() {
            Hotspot target = hotspot(10L, ContentStatus.PUBLISHED);
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(hotspotRepository.findById(10L)).thenReturn(Optional.of(target));
            when(reviewRepository.existsByUser_UserIdAndHotspot_HotspotIdAndStatusNot(
                    anyLong(), anyLong(), any())).thenReturn(false);
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
                Review saved = inv.getArgument(0);
                saved.setReviewId(100L);
                return saved;
            });

            reviewService.createReview(reviewRequest(ReviewTargetType.HOTSPOT, 10L, 5));

            ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
            verify(reviewRepository).save(captor.capture());
            Review saved = captor.getValue();
            assertEquals(ReviewStatus.ACTIVE, saved.getStatus());
            assertEquals(5, saved.getRating());
            assertSame(target, saved.getHotspot());
            assertNull(saved.getRoute());
            assertNull(saved.getStory());
            verify(ratingSummaryService).evict(ReviewTargetType.HOTSPOT, 10L);
        }

        // UTCID07 - Normal: đánh giá lộ trình thành công -> gắn đúng route, xóa cache ROUTE
        @Test
        void createReview_validRoute_attachesRouteAndEvictsRouteCache() {
            Route target = route(20L, RouteStatus.PUBLISHED);
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(routeRepository.findById(20L)).thenReturn(Optional.of(target));
            when(reviewRepository.existsByUser_UserIdAndRoute_RouteIdAndStatusNot(
                    anyLong(), anyLong(), any())).thenReturn(false);
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
                Review saved = inv.getArgument(0);
                saved.setReviewId(100L);
                return saved;
            });

            reviewService.createReview(reviewRequest(ReviewTargetType.ROUTE, 20L, 4));

            ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
            verify(reviewRepository).save(captor.capture());
            assertSame(target, captor.getValue().getRoute());
            assertNull(captor.getValue().getHotspot());
            verify(ratingSummaryService).evict(ReviewTargetType.ROUTE, 20L);
        }

        // UTCID08 - Boundary: đánh giá 1 sao (mức thấp nhất) -> vẫn hợp lệ
        @Test
        void createReview_oneStar_isAccepted() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(storyRepository.findById(30L))
                    .thenReturn(Optional.of(story(30L, ContentStatus.PUBLISHED)));
            when(reviewRepository.existsByUser_UserIdAndStory_StoryIdAndStatusNot(
                    anyLong(), anyLong(), any())).thenReturn(false);
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
                Review saved = inv.getArgument(0);
                saved.setReviewId(100L);
                return saved;
            });

            reviewService.createReview(reviewRequest(ReviewTargetType.STORY, 30L, 1));

            ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
            verify(reviewRepository).save(captor.capture());
            assertEquals(1, captor.getValue().getRating());
            verify(ratingSummaryService).evict(ReviewTargetType.STORY, 30L);
        }
    }

    // =====================================================================
    // Function: updateReview
    // =====================================================================
    @Nested
    @DisplayName("updateReview")
    class UpdateReviewTest {

        private static Review review(Long reviewId, User author) {
            return Review.builder()
                    .reviewId(reviewId)
                    .user(author)
                    .rating(4)
                    .comment("Bình thường")
                    .status(ReviewStatus.ACTIVE)
                    .hotspot(hotspot(10L, ContentStatus.PUBLISHED))
                    .medias(new ArrayList<>())
                    .reviewActions(new ArrayList<>())
                    .build();
        }

        private static ReviewUpdateRequest updateRequest() {
            ReviewUpdateRequest request = new ReviewUpdateRequest();
            request.setRating(5);
            request.setComment("Nghĩ lại thấy rất đáng đi");
            return request;
        }

        // UTCID01 - Abnormal: đánh giá không tồn tại (hoặc đã xóa)
        @Test
        void updateReview_notFound_throwsReviewNotFound() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(reviewRepository.findByReviewIdAndStatusNot(100L, ReviewStatus.DELETED))
                    .thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.updateReview(100L, updateRequest()));

            assertEquals("Không tìm thấy đánh giá với id: 100", ex.getMessage());
        }

        // UTCID02 - Abnormal: sửa đánh giá của người khác -> 403
        @Test
        void updateReview_notOwner_throwsAccessDenied() {
            when(userService.getCurrentUser()).thenReturn(user(2L));
            when(reviewRepository.findByReviewIdAndStatusNot(100L, ReviewStatus.DELETED))
                    .thenReturn(Optional.of(review(100L, user(1L))));

            AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                    () -> reviewService.updateReview(100L, updateRequest()));

            assertEquals("Bạn không có quyền chỉnh sửa đánh giá của người khác", ex.getMessage());
            verify(reviewRepository, never()).saveAndFlush(any());
        }

        // UTCID03 - Abnormal: xóa media không thuộc đánh giá này
        @Test
        void updateReview_removingForeignMedia_throwsMediaNotOwned() {
            User author = user(1L);
            Review target = review(100L, author);
            Media own = new Media();
            own.setMediaId(50L);
            target.getMedias().add(own);

            when(userService.getCurrentUser()).thenReturn(author);
            when(reviewRepository.findByReviewIdAndStatusNot(100L, ReviewStatus.DELETED))
                    .thenReturn(Optional.of(target));

            ReviewUpdateRequest request = updateRequest();
            request.setRemovedMediaIds(List.of(999L));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.updateReview(100L, request));

            assertEquals("Media không thuộc đánh giá này: [999]", ex.getMessage());
        }

        // UTCID04 - Normal: xóa media hợp lệ -> lên lịch xóa file S3 sau commit
        @Test
        void updateReview_removingOwnMedia_schedulesS3Deletion() {
            User author = user(1L);
            Review target = review(100L, author);
            Media own = new Media();
            own.setMediaId(50L);
            own.setFileUrl("https://s3/reviews/anh.png");
            target.getMedias().add(own);

            when(userService.getCurrentUser()).thenReturn(author);
            when(reviewRepository.findByReviewIdAndStatusNot(100L, ReviewStatus.DELETED))
                    .thenReturn(Optional.of(target));
            when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mediaRepository.findByReview_ReviewIdOrderByDisplayOrderAsc(100L))
                    .thenReturn(List.of());

            ReviewUpdateRequest request = updateRequest();
            request.setRemovedMediaIds(List.of(50L));

            reviewService.updateReview(100L, request);

            assertTrue(target.getMedias().isEmpty());
            verify(txCompensation).runAfterCommit(anyString(), any(Runnable.class));
        }

        // UTCID05 - Normal: chủ đánh giá sửa thành công -> gọi mapper và xóa cache điểm TB
        @Test
        void updateReview_owner_updatesAndEvictsRatingCache() {
            User author = user(1L);
            Review target = review(100L, author);
            when(userService.getCurrentUser()).thenReturn(author);
            when(reviewRepository.findByReviewIdAndStatusNot(100L, ReviewStatus.DELETED))
                    .thenReturn(Optional.of(target));
            when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mediaRepository.findByReview_ReviewIdOrderByDisplayOrderAsc(100L))
                    .thenReturn(List.of());

            ReviewUpdateRequest request = updateRequest();
            reviewService.updateReview(100L, request);

            verify(reviewMapper).updateFromRequest(target, request);
            verify(ratingSummaryService).evict(ReviewTargetType.HOTSPOT, 10L);
        }
    }

    // =====================================================================
    // Function: getSummary
    // =====================================================================
    @Nested
    @DisplayName("getSummary")
    class GetSummaryTest {

        /** Implementation thuần (không Mockito) để gọi được cả bên trong thenReturn(...). */
        private static ReviewRatingCountProjection count(int rating, long total) {
            return new ReviewRatingCountProjection() {
                @Override public Integer getRating() { return rating; }
                @Override public Long getTotal() { return total; }
            };
        }

        // UTCID01 - Abnormal: thiếu loại đối tượng
        @Test
        void getSummary_nullTargetType_throwsMissingParams() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.getSummary(null, 10L));

            assertEquals("Cần cung cấp loại đối tượng và ID đối tượng cần thống kê", ex.getMessage());
        }

        // UTCID02 - Abnormal: thiếu ID đối tượng
        @Test
        void getSummary_nullTargetId_throwsMissingParams() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.getSummary(ReviewTargetType.HOTSPOT, null));

            assertEquals("Cần cung cấp loại đối tượng và ID đối tượng cần thống kê", ex.getMessage());
        }

        // UTCID03 - Abnormal: đối tượng thống kê không tồn tại
        @Test
        void getSummary_targetNotFound_throwsTargetNotFound() {
            when(hotspotRepository.findById(10L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.getSummary(ReviewTargetType.HOTSPOT, 10L));

            assertEquals("Địa điểm không tồn tại với ID: 10", ex.getMessage());
        }

        // UTCID04 - Boundary: chưa có đánh giá nào -> điểm TB = 0, phân bố 5 mức đều bằng 0
        @Test
        void getSummary_noReviews_returnsZeroAverageAndFullDistribution() {
            when(hotspotRepository.findById(10L))
                    .thenReturn(Optional.of(hotspot(10L, ContentStatus.PUBLISHED)));
            when(reviewRepository.countRatingsByHotspotId(10L, ReviewStatus.ACTIVE))
                    .thenReturn(List.of());

            ReviewSummaryResponse result = reviewService.getSummary(ReviewTargetType.HOTSPOT, 10L);

            assertEquals(0L, result.getTotalReviews());
            assertEquals(0.0, result.getAverageRating());
            assertEquals(5, result.getRatingDistribution().size());
            assertEquals(0L, result.getRatingDistribution().get(1));
            assertEquals(0L, result.getRatingDistribution().get(5));
        }

        // UTCID05 - Normal: 2 đánh giá 5 sao + 1 đánh giá 4 sao -> TB = 4.7 (làm tròn 1 chữ số)
        @Test
        void getSummary_mixedRatings_computesRoundedAverage() {
            when(hotspotRepository.findById(10L))
                    .thenReturn(Optional.of(hotspot(10L, ContentStatus.PUBLISHED)));
            when(reviewRepository.countRatingsByHotspotId(10L, ReviewStatus.ACTIVE))
                    .thenReturn(List.of(count(5, 2L), count(4, 1L)));

            ReviewSummaryResponse result = reviewService.getSummary(ReviewTargetType.HOTSPOT, 10L);

            assertEquals(3L, result.getTotalReviews());
            assertEquals(4.7, result.getAverageRating());
            assertEquals(2L, result.getRatingDistribution().get(5));
            assertEquals(1L, result.getRatingDistribution().get(4));
            assertEquals(0L, result.getRatingDistribution().get(3));
        }

        // UTCID06 - Normal: thống kê cho STORY -> dùng đúng query theo storyId
        @Test
        void getSummary_storyTarget_usesStoryQuery() {
            when(storyRepository.findById(30L))
                    .thenReturn(Optional.of(story(30L, ContentStatus.PUBLISHED)));
            when(reviewRepository.countRatingsByStoryId(30L, ReviewStatus.ACTIVE))
                    .thenReturn(List.of(count(3, 4L)));

            ReviewSummaryResponse result = reviewService.getSummary(ReviewTargetType.STORY, 30L);

            assertEquals(4L, result.getTotalReviews());
            assertEquals(3.0, result.getAverageRating());
            verify(reviewRepository).countRatingsByStoryId(30L, ReviewStatus.ACTIVE);
            verify(reviewRepository, never()).countRatingsByHotspotId(anyLong(), any());
        }
    }

    // =====================================================================
    // Function: deleteReview / updateReviewStatus
    // =====================================================================
    @Nested
    @DisplayName("deleteReview")
    class DeleteReviewTest {

        private static Review review(Long reviewId, User author) {
            return Review.builder()
                    .reviewId(reviewId)
                    .user(author)
                    .rating(4)
                    .status(ReviewStatus.ACTIVE)
                    .hotspot(hotspot(10L, ContentStatus.PUBLISHED))
                    .medias(new ArrayList<>())
                    .reviewActions(new ArrayList<>())
                    .build();
        }

        // UTCID01 - Abnormal: đánh giá không tồn tại
        @Test
        void deleteReview_notFound_throwsReviewNotFound() {
            when(reviewRepository.findByReviewIdAndStatusNot(100L, ReviewStatus.DELETED))
                    .thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.deleteReview(100L));

            assertEquals("Không tìm thấy đánh giá với id: 100", ex.getMessage());
        }

        // UTCID02 - Normal: xóa mềm -> status DELETED và xóa cache điểm trung bình
        @Test
        void deleteReview_valid_setsDeletedAndEvictsCache() {
            Review target = review(100L, user(1L));
            when(reviewRepository.findByReviewIdAndStatusNot(100L, ReviewStatus.DELETED))
                    .thenReturn(Optional.of(target));

            reviewService.deleteReview(100L);

            assertEquals(ReviewStatus.DELETED, target.getStatus());
            verify(reviewRepository).save(target);
            verify(ratingSummaryService).evict(ReviewTargetType.HOTSPOT, 10L);
        }

        // UTCID03 - Normal: kiểm duyệt ẩn đánh giá -> đổi trạng thái HIDDEN và xóa cache
        @Test
        void updateReviewStatus_moderatorHides_changesStatusAndEvictsCache() {
            Review target = review(100L, user(1L));
            when(reviewRepository.findByReviewIdAndStatusNot(100L, ReviewStatus.DELETED))
                    .thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(user(99L));

            reviewService.updateReviewStatus(100L, ReviewStatus.HIDDEN);

            assertEquals(ReviewStatus.HIDDEN, target.getStatus());
            verify(reviewRepository).save(target);
            verify(ratingSummaryService).evict(ReviewTargetType.HOTSPOT, 10L);
        }
    }
}
