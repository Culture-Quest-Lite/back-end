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
import org.sep490.backend.common.utils.ShareTokenUtils;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.content.service.inter.RouteService;
import org.sep490.backend.module.exploration.dto.request.StartGroupQuestRoute;
import org.sep490.backend.module.exploration.dto.response.RouteParticipantDetailResponse;
import org.sep490.backend.module.exploration.dto.response.RouteParticipantResponse;
import org.sep490.backend.module.exploration.entity.RouteParticipant;
import org.sep490.backend.module.exploration.entity.enumuration.ProgressStatus;
import org.sep490.backend.module.exploration.event.RouteProgressCompletedEvent;
import org.sep490.backend.module.exploration.mapper.RouteParticipantMapper;
import org.sep490.backend.module.exploration.repository.RouteParticipantRepository;
import org.sep490.backend.module.exploration.repository.UserHotspotProgressRepository;
import org.sep490.backend.module.groupquest.entity.Group;
import org.sep490.backend.module.groupquest.entity.GroupParticipant;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupStatus;
import org.sep490.backend.module.groupquest.service.inter.GroupParticipantService;
import org.sep490.backend.module.groupquest.service.inter.GroupService;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho luồng KHÁM PHÁ TUYẾN ĐƯỜNG (Exploration).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RouteParticipantServiceImplTest {

    @Mock private RouteParticipantRepository routeParticipantRepository;
    @Mock private UserHotspotProgressRepository userHotspotProgressRepository;
    @Mock private StoryRepository storyRepository;
    @Mock private UserService userService;
    @Mock private RouteService routeService;
    @Mock private GroupService groupService;
    @Mock private GroupParticipantService groupParticipantService;
    @Mock private RouteParticipantMapper routeParticipantMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private RouteParticipantServiceImpl service;

    private User user(Long id) {
        User u = new User();
        u.setUserId(id);
        return u;
    }

    private Route route(Long id) {
        Route r = new Route();
        r.setRouteId(id);
        return r;
    }

    private Hotspot hotspot(Long id) {
        Hotspot h = new Hotspot();
        h.setHotspotId(id);
        return h;
    }

    // =====================================================================
    // Function: startRouteProgress
    // =====================================================================
    @Nested
    @DisplayName("startRouteProgress")
    class StartRouteProgressTest {

        // UTCID01 - Normal: lần đầu bắt đầu, chưa hoàn thành hết -> IN_PROGRESS (201)
        @Test
        void startRouteProgress_firstTimeNotCompleted_returns201() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(routeService.getById(10L)).thenReturn(route(10L));
            when(routeParticipantRepository.findByRoute_RouteIdAndUser_UserId(10L, 1L)).thenReturn(Optional.empty());
            when(storyRepository.findHotspotsByRouteIdOrderByIndexAsc(10L))
                    .thenReturn(List.of(hotspot(100L), hotspot(101L)));
            when(userHotspotProgressRepository.existsByUser_UserIdAndHotspot_HotspotId(anyLong(), anyLong()))
                    .thenReturn(false);
            when(routeParticipantRepository.save(any(RouteParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
            when(routeParticipantMapper.toResponse(any())).thenReturn(new RouteParticipantResponse());

            HashMap<Integer, RouteParticipantResponse> result = service.startRouteProgress(10L);

            assertTrue(result.containsKey(201));
            verify(eventPublisher, never()).publishEvent(any(RouteProgressCompletedEvent.class));
        }

        // UTCID02 - Normal: lần đầu nhưng đã check-in hết -> COMPLETED + bắn event (201)
        @Test
        void startRouteProgress_firstTimeAllCompleted_completesAndPublishesEvent() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(routeService.getById(10L)).thenReturn(route(10L));
            when(routeParticipantRepository.findByRoute_RouteIdAndUser_UserId(10L, 1L)).thenReturn(Optional.empty());
            when(storyRepository.findHotspotsByRouteIdOrderByIndexAsc(10L))
                    .thenReturn(List.of(hotspot(100L), hotspot(101L)));
            when(userHotspotProgressRepository.existsByUser_UserIdAndHotspot_HotspotId(1L, 100L)).thenReturn(true);
            when(userHotspotProgressRepository.existsByUser_UserIdAndHotspot_HotspotId(1L, 101L)).thenReturn(true);
            when(routeParticipantRepository.save(any(RouteParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
            when(routeParticipantMapper.toResponse(any())).thenReturn(new RouteParticipantResponse());

            HashMap<Integer, RouteParticipantResponse> result = service.startRouteProgress(10L);

            assertTrue(result.containsKey(201));
            verify(eventPublisher).publishEvent(any(RouteProgressCompletedEvent.class));
        }

        // UTCID03 - Abnormal: đang trong tuyến đường này rồi
        @Test
        void startRouteProgress_alreadyInProgress_throwsInProgress() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(routeService.getById(10L)).thenReturn(route(10L));
            RouteParticipant participant = RouteParticipant.builder().status(ProgressStatus.IN_PROGRESS).build();
            when(routeParticipantRepository.findByRoute_RouteIdAndUser_UserId(10L, 1L))
                    .thenReturn(Optional.of(participant));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.startRouteProgress(10L));

            assertEquals("Bạn hiện đang trong tuyến đường này", ex.getMessage());
        }

        // UTCID04 - Normal: đã bỏ dở trước đó -> bắt đầu lại IN_PROGRESS (200)
        @Test
        void startRouteProgress_previouslyAbandoned_restartsReturns200() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(routeService.getById(10L)).thenReturn(route(10L));
            RouteParticipant participant = RouteParticipant.builder().status(ProgressStatus.ABANDONED).build();
            when(routeParticipantRepository.findByRoute_RouteIdAndUser_UserId(10L, 1L))
                    .thenReturn(Optional.of(participant));
            when(storyRepository.findHotspotsByRouteIdOrderByIndexAsc(10L)).thenReturn(List.of());
            when(routeParticipantRepository.save(any(RouteParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
            when(routeParticipantMapper.toResponse(any())).thenReturn(new RouteParticipantResponse());

            HashMap<Integer, RouteParticipantResponse> result = service.startRouteProgress(10L);

            assertTrue(result.containsKey(200));
            assertEquals(ProgressStatus.IN_PROGRESS, participant.getStatus());
        }
    }

    // =====================================================================
    // Function: abandonRouteProgress
    // =====================================================================
    @Nested
    @DisplayName("abandonRouteProgress")
    class AbandonRouteProgressTest {

        // UTCID01 - Abnormal: chưa bắt đầu hành trình
        @Test
        void abandonRouteProgress_notStarted_throwsNotStarted() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            Route route = route(10L);
            route.setRouteName("Phố cổ Hà Nội");
            when(routeService.getById(10L)).thenReturn(route);
            when(routeParticipantRepository.findByRoute_RouteIdAndUser_UserId(10L, 1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.abandonRouteProgress(10L));

            assertEquals("Bạn chưa bắt đầu hành trình Phố cổ Hà Nội", ex.getMessage());
        }

        // UTCID02 - Abnormal: đã kết thúc/bỏ dở tuyến đường này
        @Test
        void abandonRouteProgress_alreadyAbandoned_throwsAlreadyEnded() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(routeService.getById(10L)).thenReturn(route(10L));
            RouteParticipant participant = RouteParticipant.builder().status(ProgressStatus.ABANDONED).build();
            when(routeParticipantRepository.findByRoute_RouteIdAndUser_UserId(10L, 1L))
                    .thenReturn(Optional.of(participant));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.abandonRouteProgress(10L));

            assertEquals("Bạn đã kết thúc tuyến đường này", ex.getMessage());
        }

        // UTCID03 - Normal: đang trong tuyến đường -> chuyển sang ABANDONED
        @Test
        void abandonRouteProgress_inProgress_setsAbandoned() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(routeService.getById(10L)).thenReturn(route(10L));
            RouteParticipant participant = RouteParticipant.builder().status(ProgressStatus.IN_PROGRESS).build();
            when(routeParticipantRepository.findByRoute_RouteIdAndUser_UserId(10L, 1L))
                    .thenReturn(Optional.of(participant));
            when(routeParticipantRepository.save(any(RouteParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
            when(routeParticipantMapper.toResponse(any())).thenReturn(new RouteParticipantResponse());

            service.abandonRouteProgress(10L);

            assertEquals(ProgressStatus.ABANDONED, participant.getStatus());
            verify(routeParticipantRepository).save(participant);
        }
    }

    // =====================================================================
    // Function: getRouteProgress
    // =====================================================================
    @Nested
    @DisplayName("getRouteProgress")
    class GetRouteProgressTest {

        // UTCID01 - Abnormal: không tìm thấy bản ghi tiến độ
        @Test
        void getRouteProgress_notFound_throwsNotFound() {
            when(routeParticipantRepository.findById(50L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.getRouteProgress(50L));

            assertEquals("User route progress not found", ex.getMessage());
        }

        // UTCID02 - Abnormal: bản ghi không thuộc về người dùng hiện tại
        @Test
        void getRouteProgress_notOwner_throwsNotStarted() {
            User owner = user(1L);
            User current = user(2L);
            RouteParticipant participant = RouteParticipant.builder()
                    .route(route(10L)).user(owner).status(ProgressStatus.IN_PROGRESS).build();
            when(routeParticipantRepository.findById(50L)).thenReturn(Optional.of(participant));
            when(userService.getCurrentUser()).thenReturn(current);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.getRouteProgress(50L));

            assertEquals("Bạn chưa bắt đầu tuyến đường này", ex.getMessage());
        }

        // UTCID03 - Normal: đúng chủ sở hữu -> trả về chi tiết tiến độ
        @Test
        void getRouteProgress_owner_returnsDetail() {
            User owner = user(1L);
            RouteParticipant participant = RouteParticipant.builder()
                    .route(route(10L)).user(owner).status(ProgressStatus.IN_PROGRESS).build();
            when(routeParticipantRepository.findById(50L)).thenReturn(Optional.of(participant));
            when(userService.getCurrentUser()).thenReturn(owner);
            when(storyRepository.getHotspotCheckInStatusByRouteAndUserNative(anyLong(), anyLong()))
                    .thenReturn(List.of());
            when(routeParticipantMapper.toDetailResponse(participant))
                    .thenReturn(new RouteParticipantDetailResponse());

            RouteParticipantDetailResponse response = service.getRouteProgress(50L);

            assertNotNull(response);
        }
    }

    // =====================================================================
    // Function: startGroupQuest
    // =====================================================================
    @Nested
    @DisplayName("startGroupQuest")
    class StartGroupQuestTest {

        private StartGroupQuestRoute request() {
            StartGroupQuestRoute request = new StartGroupQuestRoute();
            request.setGroupId(7L);
            request.setRouteId(10L);
            return request;
        }

        // UTCID01 - Abnormal: người gọi không phải trưởng nhóm
        @Test
        void startGroupQuest_notLeader_throwsNotLeader() {
            User leader = user(1L);
            Group group = new Group();
            when(userService.getCurrentUser()).thenReturn(leader);
            when(groupService.getGroup(7L)).thenReturn(group);
            when(groupParticipantService.isLeader(leader, group)).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.startGroupQuest(request()));

            assertEquals("Bạn không phải là trưởng nhóm, không thể bắt đầu hành trình nhóm", ex.getMessage());
        }

        // UTCID02 - Normal: trưởng nhóm bắt đầu -> tạo tiến độ cho thành viên ACTIVE
        @Test
        void startGroupQuest_leaderWithActiveMembers_savesParticipants() {
            User leader = user(1L);
            Group group = new Group();
            group.setGroupId(7L);
            Route route = route(10L);
            route.setTotalStops(4);
            when(userService.getCurrentUser()).thenReturn(leader);
            when(groupService.getGroup(7L)).thenReturn(group);
            when(groupParticipantService.isLeader(leader, group)).thenReturn(true);
            when(routeService.getById(10L)).thenReturn(route);
            when(storyRepository.findHotspotsByRouteIdOrderByIndexAsc(10L)).thenReturn(List.of());

            User member = user(2L);
            GroupParticipant gp = new GroupParticipant();
            gp.setUser(member);
            gp.setStatus(GroupStatus.ACTIVE);
            when(groupParticipantService.getGroupParticipants(anyLong())).thenReturn(List.of(gp));
            when(routeParticipantRepository.findByUserInAndStatus(anyList(), any())).thenReturn(List.of());
            when(routeParticipantRepository.findByUserInAndRoute(anyList(), any())).thenReturn(List.of());
            when(userHotspotProgressRepository.countCompletedHotspotsRaw(anyList(), anyList())).thenReturn(List.of());

            service.startGroupQuest(request());

            verify(routeParticipantRepository).saveAll(anyList());
        }
    }

    // =====================================================================
    // Function: joinRouteFromLink
    // =====================================================================
    @Nested
    @DisplayName("joinRouteFromLink")
    class JoinRouteFromLinkTest {

        // UTCID01 - Normal: token hợp lệ -> giải mã routeId và bắt đầu tuyến đường
        @Test
        void joinRouteFromLink_validToken_startsRoute() {
            String token = ShareTokenUtils.generateToken(10L); // token 10 ký tự, giải mã ra routeId = 10
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(routeService.getById(10L)).thenReturn(route(10L));
            when(routeParticipantRepository.findByRoute_RouteIdAndUser_UserId(10L, 1L)).thenReturn(Optional.empty());
            when(storyRepository.findHotspotsByRouteIdOrderByIndexAsc(10L)).thenReturn(List.of());
            when(routeParticipantRepository.save(any(RouteParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
            when(routeParticipantMapper.toResponse(any())).thenReturn(new RouteParticipantResponse());

            HashMap<Integer, RouteParticipantResponse> result = service.joinRouteFromLink(token);

            assertTrue(result.containsKey(201));
            verify(routeParticipantRepository).save(any(RouteParticipant.class));
        }

        // UTCID02 - Abnormal: token sai độ dài (khác 10 ký tự)
        @Test
        void joinRouteFromLink_invalidTokenLength_throwsInvalidToken() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.joinRouteFromLink("abc"));

            assertEquals("Token không hợp lệ. Độ dài bắt buộc là 10 ký tự.", ex.getMessage());
        }
    }
}
