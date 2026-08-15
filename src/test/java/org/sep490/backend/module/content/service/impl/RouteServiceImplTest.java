package org.sep490.backend.module.content.service.impl;

import org.sep490.backend.module.content.service.inter.CheckInStatusService;

import org.sep490.backend.module.content.service.inter.RatingSummaryService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.utils.SpatialUtils;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.dto.request.FinalizeCustomRouteRequest;
import org.sep490.backend.module.content.dto.request.RouteRequest;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.content.dto.response.RouteResponse;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.dto.response.TagResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.sep490.backend.module.content.entity.enumeration.RouteStatus;
import org.sep490.backend.module.content.entity.enumeration.RouteType;
import org.sep490.backend.module.content.entity.enumeration.TagStatus;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.sep490.backend.module.content.mapper.HotspotMapper;
import org.sep490.backend.module.content.mapper.RouteMapper;
import org.sep490.backend.module.content.mapper.StoryMapper;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.RouteRepository;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.content.repository.TagRepository;
import org.sep490.backend.module.content.service.inter.ImageService;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.user.service.UserService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho luồng QUẢN LÝ ROUTE (Content).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RouteServiceImplTest {

    @Mock private RouteRepository routeRepository;
    @Mock private RouteMapper routeMapper;
    @Mock private StoryRepository storyRepository;
    @Mock private HotspotRepository hotspotRepository;
    @Mock private HotspotService hotspotService;
    @Mock private UserService userService;
    @Mock private ImageService imageService;
    @Mock private StoryMapper storyMapper;
    @Mock private TagRepository tagRepository;
    @Mock private HotspotMapper hotspotMapper;

    @Mock private RatingSummaryService ratingSummaryService;
    @Mock private CheckInStatusService checkInStatusService;
    @InjectMocks private RouteServiceImpl routeService;

    @org.junit.jupiter.api.BeforeEach
    void setUpAppliers() {
        // Trong code thật các applier trả về chính đối số sau khi gán rating/check-in
        when(ratingSummaryService.applyToRoute(any(RouteResponse.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(ratingSummaryService.applyToHotspot(any(HotspotResponse.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(checkInStatusService.apply(any(HotspotResponse.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------------------------------------------------------------
    // Du lieu test dung chung - moi entity deu co gia tri cu the
    // ---------------------------------------------------------------
    private static Tag aTag() {
        Tag tag = new Tag();
        tag.setTagId(1L);
        tag.setTagName("Lich su");
        tag.setTagStatus(TagStatus.ACTIVE);
        return tag;
    }

    private static User aCurator() {
        User user = new User();
        user.setUserId(1L);
        user.setUsername("curator01");
        user.setDisplayName("Nguyen Thu Ha");
        user.setEmail("curator01@culturequest.vn");
        user.setRole(UserRole.CURATOR);
        return user;
    }

    private static Route aRoute(Long routeId, String name, RouteStatus status) {
        Route route = new Route();
        route.setRouteId(routeId);
        route.setRouteName(name);
        route.setDescription("Hanh trinh kham pha pho co Ha Noi");
        route.setStatus(status);
        return route;
    }

    private static Hotspot aHotspot(Long hotspotId, String name) {
        Hotspot hotspot = new Hotspot();
        hotspot.setHotspotId(hotspotId);
        hotspot.setHotspotName(name);
        hotspot.setStatus(ContentStatus.PUBLISHED);
        return hotspot;
    }

    private static Story aStory(Long storyId, String title) {
        Story story = new Story();
        story.setStoryId(storyId);
        story.setTitle(title);
        story.setContent("Noi dung cot truyen...");
        story.setStatus(ContentStatus.PUBLISHED);
        return story;
    }

    // =====================================================================
    // Function: create (Route)
    // =====================================================================
    @Nested
    @DisplayName("createRoute")
    class CreateRouteTest {

        private RouteRequest routeRequest(List<Long> hotspotIds) {
            RouteRequest request = new RouteRequest();
            request.setRouteName("Phố cổ Hà Nội");
            request.setTagId(1L);
            request.setHotspotIds(hotspotIds);
            return request;
        }

        // UTCID01 - Abnormal: ít hơn 4 điểm dừng
        @Test
        void createRoute_lessThanFourStops_throwsMinStops() {
            RouteRequest request = routeRequest(List.of(10L, 11L, 12L));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeService.create(request));

            assertEquals("Tuyến đường phải có ít nhất 4 điểm dừng (Hotspot)", ex.getMessage());
        }

        // UTCID02 - Abnormal: tag không tồn tại
        @Test
        void createRoute_tagNotFound_throwsTagNotExist() {
            RouteRequest request = routeRequest(List.of(10L, 11L, 12L, 13L));
            when(tagRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeService.create(request));

            assertEquals("Tag không tồn tại với ID: 1", ex.getMessage());
        }

        // UTCID03 - Abnormal: một hotspot chưa có cốt truyện nào
        @Test
        void createRoute_hotspotWithoutStory_throwsNoStory() {
            RouteRequest request = routeRequest(List.of(10L, 11L, 12L, 13L));
            Tag tag = aTag();
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
            when(routeMapper.toEntity(request)).thenReturn(aRoute(1L, "Pho co Ha Noi", RouteStatus.DRAFT));
            when(userService.getCurrentUser()).thenReturn(aCurator());
            when(routeRepository.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));

            Hotspot hotspot = aHotspot(10L, "Hồ Gươm");
            when(hotspotService.getById(10L)).thenReturn(hotspot);
            when(storyRepository.findByHotspotOrderedByIndex(10L)).thenReturn(List.of());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeService.create(request));

            assertEquals("Địa điểm Hồ Gươm chưa có cốt truyện nào!", ex.getMessage());
        }

        // UTCID04 - Normal: tạo tuyến đường thành công, trạng thái DRAFT
        @Test
        void createRoute_valid_createsDraftRoute() {
            RouteRequest request = routeRequest(List.of(10L, 11L, 12L, 13L));
            Tag tag = aTag();
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
            Route route = aRoute(1L, "Pho co Ha Noi", RouteStatus.DRAFT);
            when(routeMapper.toEntity(request)).thenReturn(route);
            when(userService.getCurrentUser()).thenReturn(aCurator());
            when(routeRepository.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));

            Hotspot hotspot = aHotspot(10L, "Hồ Gươm");
            hotspot.setLocation(SpatialUtils.fromCoordinates(105.85, 21.02));
            Story story = aStory(30L, "Truyen thuyet Rua Vang");
            story.setHotspot(hotspot);
            when(hotspotService.getById(anyLong())).thenReturn(hotspot);
            when(storyRepository.findByHotspotOrderedByIndex(anyLong())).thenReturn(List.of(story));
            when(storyRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            when(storyRepository.findByHotspotOrderedByRouteTag(anyLong(), anyList())).thenReturn(List.of());
            when(routeMapper.toResponse(any(Route.class))).thenReturn(new RouteResponse());
            when(hotspotMapper.toResponse(any(Hotspot.class))).thenReturn(new HotspotResponse());
            when(storyMapper.toResponse(any(Story.class))).thenReturn(new StoryResponse());
            when(storyMapper.toTagResponse(any(Tag.class))).thenReturn(new TagResponse());

            RouteResponse response = routeService.create(request);

            assertNotNull(response);
            assertEquals(RouteStatus.DRAFT, route.getStatus());
            assertEquals(4, route.getTotalStops());
            verify(routeRepository).save(route);
        }

        // UTCID05 - Boundary: đúng 4 điểm dừng (ngưỡng tối thiểu) -> chấp nhận
        @Test
        void createRoute_exactlyFourStops_isAccepted() {
            RouteRequest request = routeRequest(List.of(10L, 11L, 12L, 13L));
            stubValidRouteCreation(request, aRoute(1L, "Pho co Ha Noi", RouteStatus.DRAFT));

            assertDoesNotThrow(() -> routeService.create(request));
        }

        private void stubValidRouteCreation(RouteRequest request, Route route) {
            Tag tag = aTag();
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
            when(routeMapper.toEntity(request)).thenReturn(route);
            when(userService.getCurrentUser()).thenReturn(aCurator());
            when(routeRepository.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));

            Hotspot hotspot = aHotspot(10L, "Hồ Gươm");
            hotspot.setLocation(SpatialUtils.fromCoordinates(105.85, 21.02));
            Story story = aStory(30L, "Truyen thuyet Rua Vang");
            story.setHotspot(hotspot);
            when(hotspotService.getById(anyLong())).thenReturn(hotspot);
            when(storyRepository.findByHotspotOrderedByIndex(anyLong())).thenReturn(List.of(story));
            when(storyRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            when(storyRepository.findByHotspotOrderedByRouteTag(anyLong(), anyList())).thenReturn(List.of());
            when(routeMapper.toResponse(any(Route.class))).thenReturn(new RouteResponse());
            when(hotspotMapper.toResponse(any(Hotspot.class))).thenReturn(new HotspotResponse());
            when(storyMapper.toResponse(any(Story.class))).thenReturn(new StoryResponse());
            when(storyMapper.toTagResponse(any(Tag.class))).thenReturn(new TagResponse());
        }
    }

    // =====================================================================
    // Function: update (Route)
    // =====================================================================
    @Nested
    @DisplayName("updateRoute")
    class UpdateRouteTest {

        private RouteRequest routeRequest(List<Long> hotspotIds) {
            RouteRequest request = new RouteRequest();
            request.setRouteName("Phố cổ Hà Nội");
            request.setTagId(1L);
            request.setHotspotIds(hotspotIds);
            return request;
        }

        // UTCID01 - Abnormal: ít hơn 4 điểm dừng -> chặn cập nhật
        @Test
        void updateRoute_lessThanFourStops_throwsMinStops() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeService.update(1L, routeRequest(List.of(10L, 11L, 12L))));

            assertEquals("Tuyến đường phải có ít nhất 4 điểm dừng", ex.getMessage());
            verify(routeRepository, never()).save(any());
        }

        // UTCID02 - Abnormal: tag không tồn tại -> chặn cập nhật
        @Test
        void updateRoute_tagNotFound_throwsTagNotExist() {
            when(tagRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeService.update(1L, routeRequest(List.of(10L, 11L, 12L, 13L))));

            assertEquals("Tag không tồn tại với ID: 1", ex.getMessage());
        }

        // UTCID03 - Abnormal: tuyến đường không tồn tại
        @Test
        void updateRoute_routeNotFound_throwsRouteNotExist() {
            when(tagRepository.findById(1L)).thenReturn(Optional.of(aTag()));
            when(routeRepository.findById(99L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeService.update(99L, routeRequest(List.of(10L, 11L, 12L, 13L))));

            assertEquals("Tuyến đường không tồn tại", ex.getMessage());
        }

        // UTCID04 - Normal: cập nhật hợp lệ -> gán lại tag và cập nhật số điểm dừng
        @Test
        void updateRoute_valid_updatesTagAndTotalStops() {
            Route route = aRoute(1L, "Pho co Ha Noi", RouteStatus.DRAFT);
            route.setRouteId(1L);
            Tag tag = aTag();

            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
            when(routeRepository.findById(1L)).thenReturn(Optional.of(route));
            when(routeRepository.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));
            stubStoryProcessing();

            RouteRequest request = routeRequest(List.of(10L, 11L, 12L, 13L, 14L));
            routeService.update(1L, request);

            verify(routeMapper).updateFromRequest(route, request);
            assertSame(tag, route.getTag());
            assertEquals(5, route.getTotalStops());
            verify(routeRepository).save(route);
        }

        // UTCID05 - Normal: story cũ của tuyến bị gỡ liên kết trước khi gắn danh sách mới
        @Test
        void updateRoute_detachesOldStoriesBeforeReattaching() {
            Route route = aRoute(1L, "Pho co Ha Noi", RouteStatus.DRAFT);
            route.setRouteId(1L);
            Story oldStory = aStory(30L, "Truyen thuyet Rua Vang");
            oldStory.setRoute(route);
            oldStory.setOrderIndex(1);
            oldStory.setDistanceToNext(500.0);

            Tag tag = aTag();
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
            when(routeRepository.findById(1L)).thenReturn(Optional.of(route));
            when(routeRepository.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));
            when(storyRepository.findByRoute_RouteIdOrderByOrderIndexAsc(1L))
                    .thenReturn(List.of(oldStory));
            stubStoryProcessing();

            routeService.update(1L, routeRequest(List.of(10L, 11L, 12L, 13L)));

            assertNull(oldStory.getRoute());
            assertNull(oldStory.getOrderIndex());
            assertNull(oldStory.getDistanceToNext());
        }

        private void stubStoryProcessing() {
            Hotspot hotspot = aHotspot(10L, "Hồ Gươm");
            hotspot.setLocation(SpatialUtils.fromCoordinates(105.85, 21.02));
            Story story = aStory(30L, "Truyen thuyet Rua Vang");
            story.setHotspot(hotspot);
            when(hotspotService.getById(anyLong())).thenReturn(hotspot);
            when(storyRepository.findByHotspotOrderedByIndex(anyLong())).thenReturn(List.of(story));
            when(storyRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            when(storyRepository.findByHotspotOrderedByRouteTag(anyLong(), anyList())).thenReturn(List.of());
            when(routeMapper.toResponse(any(Route.class))).thenReturn(new RouteResponse());
            when(hotspotMapper.toResponse(any(Hotspot.class))).thenReturn(new HotspotResponse());
            when(storyMapper.toResponse(any(Story.class))).thenReturn(new StoryResponse());
            when(storyMapper.toTagResponse(any(Tag.class))).thenReturn(new TagResponse());
        }
    }

    // =====================================================================
    // Function: delete / getById (Route)
    // =====================================================================
    @Nested
    @DisplayName("deleteRoute")
    class DeleteRouteTest {

        // UTCID01 - Abnormal: tuyến đường không tồn tại
        @Test
        void deleteRoute_notFound_throwsRouteNotExist() {
            when(routeRepository.findById(99L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeService.delete(99L));

            assertEquals("Tuyến đường không tồn tại", ex.getMessage());
            verify(routeRepository, never()).save(any());
        }

        // UTCID02 - Normal: xóa mềm tuyến đường -> trạng thái DELETED
        @Test
        void deleteRoute_existing_softDeletesRoute() {
            Route route = aRoute(1L, "Pho co Ha Noi", RouteStatus.DRAFT);
            route.setRouteId(1L);
            route.setStatus(RouteStatus.PUBLISHED);
            when(routeRepository.findById(1L)).thenReturn(Optional.of(route));
            when(storyRepository.findByRoute_RouteIdOrderByOrderIndexAsc(1L)).thenReturn(List.of());

            routeService.delete(1L);

            assertEquals(RouteStatus.DELETED, route.getStatus());
            verify(routeRepository).save(route);
        }

        // UTCID03 - Normal: story thuộc tuyến cũng bị xóa và gỡ liên kết
        @Test
        void deleteRoute_alsoDeletesAndDetachesStories() {
            Route route = aRoute(1L, "Pho co Ha Noi", RouteStatus.DRAFT);
            route.setRouteId(1L);
            route.setStatus(RouteStatus.PUBLISHED);
            Story story = aStory(30L, "Truyen thuyet Rua Vang");
            story.setRoute(route);
            story.setOrderIndex(1);
            story.setDistanceToNext(500.0);
            story.setStatus(ContentStatus.PUBLISHED);

            when(routeRepository.findById(1L)).thenReturn(Optional.of(route));
            when(storyRepository.findByRoute_RouteIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(story));

            routeService.delete(1L);

            assertNull(story.getRoute());
            assertNull(story.getOrderIndex());
            assertNull(story.getDistanceToNext());
            assertEquals(ContentStatus.DELETED, story.getStatus());
            verify(storyRepository).saveAll(List.of(story));
        }

        // UTCID04 - Normal: getById trả về entity khi tồn tại
        @Test
        void getById_existing_returnsRoute() {
            Route route = aRoute(1L, "Pho co Ha Noi", RouteStatus.DRAFT);
            route.setRouteId(1L);
            when(routeRepository.findById(1L)).thenReturn(Optional.of(route));

            assertSame(route, routeService.getById(1L));
        }
    }

    // =====================================================================
    // Function: removeHotspotFromRoute
    // =====================================================================
    @Nested
    @DisplayName("removeHotspotFromRoute")
    class RemoveHotspotFromRouteTest {

        // UTCID01 - Abnormal: tuyến đường không tồn tại
        @Test
        void removeHotspot_routeNotFound_throwsRouteNotExist() {
            when(routeRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class,
                    () -> routeService.removeHotspotFromRoute(99L, 10L));
        }

        // UTCID02 - Boundary: tuyến chỉ còn đúng 4 điểm dừng -> không cho xóa thêm
        @Test
        void removeHotspot_atMinimumStops_throwsMinStops() {
            Route route = aRoute(1L, "Pho co Ha Noi", RouteStatus.DRAFT);
            route.setRouteId(1L);
            route.setTotalStops(4);
            when(routeRepository.findById(1L)).thenReturn(Optional.of(route));
            when(hotspotService.getById(10L)).thenReturn(aHotspot(10L, "Ho Guom"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeService.removeHotspotFromRoute(1L, 10L));

            assertEquals("Tuyến đường hiện có 4 điểm dừng, thêm điểm dừng mới trước khi xóa",
                    ex.getMessage());
        }

        // UTCID03 - Boundary: tuyến có ít hơn 4 điểm dừng -> cũng bị chặn
        @Test
        void removeHotspot_belowMinimumStops_throwsMinStops() {
            Route route = aRoute(1L, "Pho co Ha Noi", RouteStatus.DRAFT);
            route.setRouteId(1L);
            route.setTotalStops(3);
            when(routeRepository.findById(1L)).thenReturn(Optional.of(route));
            when(hotspotService.getById(10L)).thenReturn(aHotspot(10L, "Ho Guom"));

            assertThrows(BusinessException.class,
                    () -> routeService.removeHotspotFromRoute(1L, 10L));
        }
    }

    // =====================================================================
    // Function: findRecordingCustomRouteByUserId
    // =====================================================================
    @Nested
    @DisplayName("findRecordingCustomRouteByUserId")
    class FindRecordingCustomRouteTest {

        // UTCID01 - Abnormal: người dùng không có hành trình đang ghi
        @Test
        void findRecording_noRecordingRoute_throwsNotFound() {
            User user = aCurator();
            user.setUserId(1L);
            when(userService.getUserById(1L)).thenReturn(user);
            when(routeRepository.findByCreatedByAndTypeAndStatus(
                    user, RouteType.CUSTOM, RouteStatus.RECORDING)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeService.findRecordingCustomRouteByUserId(1L));

            assertEquals("Không tìm thấy hành trình đang ghi lại của người dùng này.", ex.getMessage());
        }

        // UTCID02 - Normal: trả về hành trình CUSTOM đang ở trạng thái RECORDING
        @Test
        void findRecording_hasRecordingRoute_returnsIt() {
            User user = aCurator();
            user.setUserId(1L);
            Route recording = aRoute(2L, "Hanh trinh cua toi", RouteStatus.RECORDING);
            recording.setRouteId(7L);
            recording.setType(RouteType.CUSTOM);
            recording.setStatus(RouteStatus.RECORDING);

            when(userService.getUserById(1L)).thenReturn(user);
            when(routeRepository.findByCreatedByAndTypeAndStatus(
                    user, RouteType.CUSTOM, RouteStatus.RECORDING)).thenReturn(Optional.of(recording));

            assertSame(recording, routeService.findRecordingCustomRouteByUserId(1L));
        }

        // UTCID03 - Abnormal: người dùng không tồn tại -> lỗi lan từ UserService
        @Test
        void findRecording_userNotFound_propagatesError() {
            when(userService.getUserById(99L))
                    .thenThrow(new BusinessException("Không tìm thấy thông tin người dùng"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeService.findRecordingCustomRouteByUserId(99L));

            assertEquals("Không tìm thấy thông tin người dùng", ex.getMessage());
        }
    }
}
