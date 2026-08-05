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
import org.sep490.backend.module.content.entity.enumeration.RouteStatus;
import org.sep490.backend.module.content.entity.enumeration.RouteType;
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
            Tag tag = new Tag();
            tag.setTagId(1L);
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
            when(routeMapper.toEntity(request)).thenReturn(new Route());
            when(userService.getCurrentUser()).thenReturn(new User());
            when(routeRepository.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));

            Hotspot hotspot = new Hotspot();
            hotspot.setHotspotId(10L);
            hotspot.setHotspotName("Hồ Gươm");
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
            Tag tag = new Tag();
            tag.setTagId(1L);
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
            Route route = new Route();
            when(routeMapper.toEntity(request)).thenReturn(route);
            when(userService.getCurrentUser()).thenReturn(new User());
            when(routeRepository.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));

            Hotspot hotspot = new Hotspot();
            hotspot.setHotspotId(10L);
            hotspot.setHotspotName("Hồ Gươm");
            hotspot.setLocation(SpatialUtils.fromCoordinates(105.85, 21.02));
            Story story = new Story();
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
    }

    // =====================================================================
    // Function: recordJourney
    // =====================================================================
    @Nested
    @DisplayName("recordJourney")
    class RecordJourneyTest {

        private User creator() {
            User u = new User();
            u.setUserId(1L);
            u.setDisplayName("Traveler");
            return u;
        }

        // UTCID01 - Abnormal: đang có một hành trình ghi lại chưa hoàn thành
        @Test
        void recordJourney_alreadyRecording_throwsAlreadyRecording() {
            User creator = creator();
            when(userService.getCurrentUser()).thenReturn(creator);
            when(routeRepository.findByCreatedByAndTypeAndStatus(creator, RouteType.CUSTOM, RouteStatus.RECORDING))
                    .thenReturn(Optional.of(new Route()));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeService.recordJourney());

            assertEquals("Người dùng đã có hành trình đang ghi lại. "
                    + "Vui lòng hoàn thành hành trình trước khi bắt đầu hành trình mới.", ex.getMessage());
        }

        // UTCID02 - Abnormal: không tìm thấy tag mặc định "Hành Trình Cá Nhân"
        @Test
        void recordJourney_defaultTagMissing_throwsTagNotFound() {
            User creator = creator();
            when(userService.getCurrentUser()).thenReturn(creator);
            when(routeRepository.findByCreatedByAndTypeAndStatus(creator, RouteType.CUSTOM, RouteStatus.RECORDING))
                    .thenReturn(Optional.empty());
            when(tagRepository.findByTagName("Hành Trình Cá Nhân")).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeService.recordJourney());

            assertEquals("Không tìm thấy tag 'Hành trình cá nhân'", ex.getMessage());
        }

        // UTCID03 - Normal: tạo hành trình cá nhân mới ở trạng thái RECORDING
        @Test
        void recordJourney_valid_createsRecordingRoute() {
            User creator = creator();
            when(userService.getCurrentUser()).thenReturn(creator);
            when(routeRepository.findByCreatedByAndTypeAndStatus(creator, RouteType.CUSTOM, RouteStatus.RECORDING))
                    .thenReturn(Optional.empty());
            Tag tag = new Tag();
            tag.setTagId(9L);
            when(tagRepository.findByTagName("Hành Trình Cá Nhân")).thenReturn(Optional.of(tag));
            when(routeRepository.countByCreatedBy(creator)).thenReturn(0);
            when(routeRepository.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));
            when(routeMapper.toResponse(any(Route.class))).thenReturn(new RouteResponse());
            when(storyMapper.toTagResponse(any(Tag.class))).thenReturn(new TagResponse());

            RouteResponse response = routeService.recordJourney();

            assertNotNull(response);
            verify(routeRepository).save(argThat(r ->
                    r.getStatus() == RouteStatus.RECORDING && r.getType() == RouteType.CUSTOM));
        }
    }

    // =====================================================================
    // Function: finishRecordJourney
    // =====================================================================
    @Nested
    @DisplayName("finishRecordJourney")
    class FinishRecordJourneyTest {

        private Route recordingRouteWithStops(int stops) {
            Route route = new Route();
            route.setStatus(RouteStatus.RECORDING);
            for (int i = 0; i < stops; i++) {
                route.getStories().add(new Story());
            }
            return route;
        }

        // UTCID01 - Abnormal: không có hành trình đang ghi lại
        @Test
        void finishRecordJourney_noRecordingJourney_throwsNotFound() {
            User user = new User();
            user.setUserId(1L);
            when(userService.getCurrentUser()).thenReturn(user);
            when(userService.getUserById(1L)).thenReturn(user);
            when(routeRepository.findByCreatedByAndTypeAndStatus(user, RouteType.CUSTOM, RouteStatus.RECORDING))
                    .thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeService.finishRecordJourney());

            assertEquals("Không tìm thấy hành trình đang ghi lại của người dùng này.", ex.getMessage());
        }

        // UTCID02 - Abnormal: hành trình có ít hơn 4 điểm dừng
        @Test
        void finishRecordJourney_lessThanFourStops_throwsMinStops() {
            User user = new User();
            user.setUserId(1L);
            when(userService.getCurrentUser()).thenReturn(user);
            when(userService.getUserById(1L)).thenReturn(user);
            when(routeRepository.findByCreatedByAndTypeAndStatus(user, RouteType.CUSTOM, RouteStatus.RECORDING))
                    .thenReturn(Optional.of(recordingRouteWithStops(2)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeService.finishRecordJourney());

            assertEquals("Hành trình cá nhân phải có ít nhất 4 điểm dừng (Hotspot)", ex.getMessage());
        }

        // UTCID03 - Normal: đủ >= 4 điểm dừng -> chuyển sang DRAFT
        @Test
        void finishRecordJourney_enoughStops_setsDraft() {
            User user = new User();
            user.setUserId(1L);
            Route route = recordingRouteWithStops(4);
            when(userService.getCurrentUser()).thenReturn(user);
            when(userService.getUserById(1L)).thenReturn(user);
            when(routeRepository.findByCreatedByAndTypeAndStatus(user, RouteType.CUSTOM, RouteStatus.RECORDING))
                    .thenReturn(Optional.of(route));
            when(routeRepository.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));
            when(routeMapper.toResponse(any(Route.class))).thenReturn(new RouteResponse());

            routeService.finishRecordJourney();

            assertEquals(RouteStatus.DRAFT, route.getStatus());
        }
    }

    // =====================================================================
    // Function: finalizeCustomRoute
    // =====================================================================
    @Nested
    @DisplayName("finalizeCustomRoute")
    class FinalizeCustomRouteTest {

        private FinalizeCustomRouteRequest request() {
            FinalizeCustomRouteRequest request = new FinalizeCustomRouteRequest();
            request.setRouteId(10L);
            request.setDescription("Chuyến đi của tôi");
            return request;
        }

        private Route route(RouteStatus status, RouteType type, User owner) {
            Route route = new Route();
            route.setRouteId(10L);
            route.setStatus(status);
            route.setType(type);
            route.setCreatedBy(owner);
            return route;
        }

        // UTCID01 - Abnormal: hành trình không ở trạng thái DRAFT
        @Test
        void finalizeCustomRoute_notDraft_throwsNotDraft() {
            User user = new User();
            when(routeRepository.findById(10L))
                    .thenReturn(Optional.of(route(RouteStatus.RECORDING, RouteType.CUSTOM, user)));
            when(userService.getCurrentUser()).thenReturn(user);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeService.finalizeCustomRoute(request()));

            assertEquals("Chỉ có thể hoàn tất hành trình cá nhân đang ở trạng thái DRAFT", ex.getMessage());
        }

        // UTCID02 - Abnormal: hành trình không phải loại CUSTOM
        @Test
        void finalizeCustomRoute_notCustom_throwsNotCustom() {
            User user = new User();
            when(routeRepository.findById(10L))
                    .thenReturn(Optional.of(route(RouteStatus.DRAFT, RouteType.OFFICIAL, user)));
            when(userService.getCurrentUser()).thenReturn(user);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeService.finalizeCustomRoute(request()));

            assertEquals("Chỉ có thể hoàn tất hành trình cá nhân có loại CUSTOM", ex.getMessage());
        }

        // UTCID03 - Abnormal: không phải chủ sở hữu hành trình
        @Test
        void finalizeCustomRoute_notOwner_throwsNotOwner() {
            User owner = new User();
            owner.setUserId(1L);
            User current = new User();
            current.setUserId(2L);
            when(routeRepository.findById(10L))
                    .thenReturn(Optional.of(route(RouteStatus.DRAFT, RouteType.CUSTOM, owner)));
            when(userService.getCurrentUser()).thenReturn(current);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeService.finalizeCustomRoute(request()));

            assertEquals("Người dùng chỉ được hoàn thành hành trình cá nhân của mình", ex.getMessage());
        }

        // UTCID04 - Normal: hợp lệ -> chuyển sang PUBLISHED
        @Test
        void finalizeCustomRoute_valid_setsPublished() {
            User owner = new User();
            owner.setUserId(1L);
            Route route = route(RouteStatus.DRAFT, RouteType.CUSTOM, owner);
            when(routeRepository.findById(10L)).thenReturn(Optional.of(route));
            when(userService.getCurrentUser()).thenReturn(owner);
            when(routeRepository.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));
            when(routeMapper.toResponse(any(Route.class))).thenReturn(new RouteResponse());

            routeService.finalizeCustomRoute(request());

            assertEquals(RouteStatus.PUBLISHED, route.getStatus());
        }
    }
}
