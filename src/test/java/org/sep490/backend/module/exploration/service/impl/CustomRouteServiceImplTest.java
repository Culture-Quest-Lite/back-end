package org.sep490.backend.module.exploration.service.impl;

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
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.dto.request.FinalizeCustomRouteRequest;
import org.sep490.backend.module.content.dto.response.RouteResponse;
import org.sep490.backend.module.content.dto.response.TagResponse;
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
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.content.service.inter.ImageService;
import org.sep490.backend.module.content.service.inter.RouteService;
import org.sep490.backend.module.user.service.UserService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomRouteServiceImplTest {

    @Mock private RouteRepository routeRepository;
    @Mock private RouteMapper routeMapper;
    @Mock private UserService userService;
    @Mock private RouteService routeService;
    @Mock private StoryMapper storyMapper;
    @Mock private TagRepository tagRepository;

    @InjectMocks
    private CustomRouteServiceImpl customRouteService;

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
                    () -> customRouteService.recordJourney());

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
                    () -> customRouteService.recordJourney());

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

            RouteResponse response = customRouteService.recordJourney();

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
            when(routeService.findRecordingCustomRouteByUserId(user.getUserId()))
                    .thenThrow(new BusinessException("Không tìm thấy hành trình đang ghi lại của người dùng này."));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> customRouteService.finishRecordJourney());

            assertEquals("Không tìm thấy hành trình đang ghi lại của người dùng này.", ex.getMessage());
        }

        // UTCID02 - Abnormal: hành trình có ít hơn 4 điểm dừng
        @Test
        void finishRecordJourney_lessThanFourStops_throwsMinStops() {
            User user = new User();
            user.setUserId(1L);
            when(userService.getCurrentUser()).thenReturn(user);
            when(userService.getUserById(1L)).thenReturn(user);
            when(routeService.findRecordingCustomRouteByUserId(user.getUserId()))
                    .thenReturn(recordingRouteWithStops(2));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> customRouteService.finishRecordJourney());

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
            when(routeService.findRecordingCustomRouteByUserId(user.getUserId()))
                    .thenReturn(route);
            when(routeRepository.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));
            when(routeMapper.toResponse(any(Route.class))).thenReturn(new RouteResponse());

            customRouteService.finishRecordJourney();

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
            user.setUserId(1L);
            Route route = route(RouteStatus.RECORDING, RouteType.CUSTOM, user);
            when(routeService.getById(10L)).thenReturn(route);
            when(userService.getCurrentUser()).thenReturn(user);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> customRouteService.finalizeCustomRoute(request()));

            assertEquals("Chỉ có thể hoàn tất hành trình cá nhân đang ở trạng thái DRAFT", ex.getMessage());
        }

        // UTCID02 - Abnormal: hành trình không phải loại CUSTOM
        @Test
        void finalizeCustomRoute_notCustom_throwsNotCustom() {
            User user = new User();
            user.setUserId(1L);
            Route route = route(RouteStatus.DRAFT, RouteType.OFFICIAL, user);
            when(routeService.getById(10L)).thenReturn(route);
            when(userService.getCurrentUser()).thenReturn(user);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> customRouteService.finalizeCustomRoute(request()));

            assertEquals("Chỉ có thể hoàn tất hành trình cá nhân có loại CUSTOM", ex.getMessage());
        }

        // UTCID03 - Abnormal: không phải chủ sở hữu hành trình
        @Test
        void finalizeCustomRoute_notOwner_throwsNotOwner() {
            User owner = new User();
            owner.setUserId(1L);
            User current = new User();
            current.setUserId(2L);
            Route route = route(RouteStatus.DRAFT, RouteType.CUSTOM, owner);
            when(routeService.getById(10L)).thenReturn(route);
            when(userService.getCurrentUser()).thenReturn(current);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> customRouteService.finalizeCustomRoute(request()));

            assertEquals("Người dùng chỉ được hoàn thành hành trình cá nhân của mình", ex.getMessage());
        }

        // UTCID04 - Normal: hợp lệ -> chuyển sang PUBLISHED
        @Test
        void finalizeCustomRoute_valid_setsPublished() {
            User owner = new User();
            owner.setUserId(1L);
            Route route = route(RouteStatus.DRAFT, RouteType.CUSTOM, owner);
            when(routeService.getById(10L)).thenReturn(route);
            when(userService.getCurrentUser()).thenReturn(owner);
            when(routeRepository.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));
            when(routeMapper.toResponse(any(Route.class))).thenReturn(new RouteResponse());

            customRouteService.finalizeCustomRoute(request());

            assertEquals(RouteStatus.PUBLISHED, route.getStatus());
        }
    }
}