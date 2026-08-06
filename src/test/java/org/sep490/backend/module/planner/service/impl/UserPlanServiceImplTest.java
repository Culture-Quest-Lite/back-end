package org.sep490.backend.module.planner.service.impl;

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
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.exploration.repository.UserHotspotProgressRepository;
import org.sep490.backend.module.planner.dto.request.CreateCustomPlanRequest;
import org.sep490.backend.module.planner.dto.request.PlanStopRequest;
import org.sep490.backend.module.planner.dto.response.PlanHotspotResponse;
import org.sep490.backend.module.planner.dto.response.UserPlanResponse;
import org.sep490.backend.module.planner.entity.PlanHotspot;
import org.sep490.backend.module.planner.entity.UserPlan;
import org.sep490.backend.module.planner.entity.enumeration.PlanStatus;
import org.sep490.backend.module.planner.mapper.PlanMapper;
import org.sep490.backend.module.planner.repository.UserPlanRepository;
import org.sep490.backend.module.user.service.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho KẾ HOẠCH CÁ NHÂN (User Plan).
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserPlanServiceImplTest {

    @Mock private UserPlanRepository userPlanRepository;
    @Mock private UserHotspotProgressRepository userHotspotProgressRepository;
    @Mock private HotspotService hotspotService;
    @Mock private UserService userService;
    @Mock private PlanMapper planMapper;

    @InjectMocks private UserPlanServiceImpl userPlanService;

    private static User user(Long userId) {
        User user = new User();
        user.setUserId(userId);
        return user;
    }

    private static UserPlan plan(Long planId, User owner, PlanStatus status, int stopCount) {
        UserPlan plan = UserPlan.builder()
                .userPlanId(planId)
                .user(owner)
                .name("Cuối tuần ở Đà Lạt")
                .description("Đi 3 điểm trong 1 ngày")
                .status(status)
                .totalStops(stopCount)
                .planHotspots(new ArrayList<>())
                .build();
        for (int i = 0; i < stopCount; i++) {
            plan.getPlanHotspots().add(new PlanHotspot());
        }
        return plan;
    }

    private static Hotspot hotspot(Long id) {
        Hotspot hotspot = new Hotspot();
        hotspot.setHotspotId(id);
        return hotspot;
    }

    private static CreateCustomPlanRequest createRequest(List<Long> hotspotIds) {
        CreateCustomPlanRequest request = new CreateCustomPlanRequest();
        request.setName("Cuối tuần ở Đà Lạt");
        request.setDescription("Đi 3 điểm trong 1 ngày");
        List<PlanStopRequest> stops = new ArrayList<>();
        for (Long hotspotId : hotspotIds) {
            PlanStopRequest stop = new PlanStopRequest();
            stop.setHotspotId(hotspotId);
            stop.setUserNote("Ghi chú cho điểm " + hotspotId);
            stops.add(stop);
        }
        request.setStops(stops);
        return request;
    }

    /** Response có n điểm dừng để toResponseWithProgress() tính được tiến độ. */
    private void stubMapperResponse(int stopCount, Long... hotspotIds) {
        when(planMapper.toResponse(any(UserPlan.class))).thenAnswer(inv -> {
            UserPlanResponse response = new UserPlanResponse();
            List<PlanHotspotResponse> stops = new ArrayList<>();
            for (int i = 0; i < stopCount; i++) {
                PlanHotspotResponse stop = new PlanHotspotResponse();
                HotspotResponse hotspotResponse = new HotspotResponse();
                hotspotResponse.setHotspotId(hotspotIds[i]);
                hotspotResponse.setStories(new ArrayList<>());
                stop.setHotspot(hotspotResponse);
                stops.add(stop);
            }
            response.setStops(stops);
            return response;
        });
    }

    // =====================================================================
    // Function: start
    // =====================================================================
    @Nested
    @DisplayName("start")
    class StartTest {

        // UTCID01 - Abnormal: kế hoạch không tồn tại
        @Test
        void start_planNotFound_throwsPlanNotFound() {
            when(userPlanRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userPlanService.start(1L));

            assertEquals("Kế hoạch không tồn tại", ex.getMessage());
        }

        // UTCID02 - Abnormal: kế hoạch của người khác -> không có quyền
        @Test
        void start_notOwner_throwsNoPermission() {
            when(userPlanRepository.findById(1L))
                    .thenReturn(Optional.of(plan(1L, user(1L), PlanStatus.READY, 3)));
            when(userService.getCurrentUser()).thenReturn(user(2L));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userPlanService.start(1L));

            assertEquals("Bạn không có quyền truy cập kế hoạch này", ex.getMessage());
            verify(userPlanRepository, never()).save(any());
        }

        // UTCID03 - Abnormal: kế hoạch chưa có điểm dừng nào
        @Test
        void start_noStops_throwsNoStops() {
            User owner = user(1L);
            when(userPlanRepository.findById(1L))
                    .thenReturn(Optional.of(plan(1L, owner, PlanStatus.READY, 0)));
            when(userService.getCurrentUser()).thenReturn(owner);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userPlanService.start(1L));

            assertEquals("Kế hoạch chưa có điểm dừng nào", ex.getMessage());
            verify(userPlanRepository, never()).save(any());
        }

        // UTCID04 - Abnormal: kế hoạch đã bắt đầu rồi
        @Test
        void start_alreadyStarted_throwsAlreadyStarted() {
            User owner = user(1L);
            when(userPlanRepository.findById(1L))
                    .thenReturn(Optional.of(plan(1L, owner, PlanStatus.STARTED, 3)));
            when(userService.getCurrentUser()).thenReturn(owner);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userPlanService.start(1L));

            assertEquals("Bạn đã bắt đầu kế hoạch này rồi", ex.getMessage());
            verify(userPlanRepository, never()).save(any());
        }

        // UTCID05 - Normal: bắt đầu hành trình -> status STARTED và ghi thời điểm bắt đầu
        @Test
        void start_readyPlan_setsStartedStatusAndTimestamp() {
            User owner = user(1L);
            UserPlan target = plan(1L, owner, PlanStatus.READY, 2);
            when(userPlanRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(owner);
            when(userPlanRepository.save(any(UserPlan.class))).thenAnswer(inv -> inv.getArgument(0));
            stubMapperResponse(2, 10L, 11L);

            userPlanService.start(1L);

            assertEquals(PlanStatus.STARTED, target.getStatus());
            assertNotNull(target.getStartedAt());
            verify(userPlanRepository).save(target);
        }
    }

    // =====================================================================
    // Function: create
    // =====================================================================
    @Nested
    @DisplayName("create")
    class CreateTest {

        // UTCID01 - Normal: tạo kế hoạch -> status READY, đánh số điểm dừng từ 1
        @Test
        void create_validRequest_createsReadyPlanWithOrderedStops() {
            User owner = user(1L);
            UserPlan mapped = UserPlan.builder().planHotspots(new ArrayList<>()).build();
            when(userService.getCurrentUser()).thenReturn(owner);
            when(planMapper.toEntity(any(CreateCustomPlanRequest.class))).thenReturn(mapped);
            when(planMapper.toEntity(any(PlanStopRequest.class))).thenAnswer(inv -> new PlanHotspot());
            when(hotspotService.getById(anyLong())).thenAnswer(inv -> hotspot(inv.getArgument(0)));
            when(userPlanRepository.save(any(UserPlan.class))).thenAnswer(inv -> inv.getArgument(0));
            stubMapperResponse(3, 10L, 11L, 12L);

            userPlanService.create(createRequest(List.of(10L, 11L, 12L)));

            assertSame(owner, mapped.getUser());
            assertEquals(PlanStatus.READY, mapped.getStatus());
            assertEquals(3, mapped.getTotalStops());
            assertEquals(3, mapped.getPlanHotspots().size());
            assertEquals(1, mapped.getPlanHotspots().get(0).getStopIndex());
            assertEquals(3, mapped.getPlanHotspots().get(2).getStopIndex());
        }

        // UTCID02 - Normal: chưa check-in điểm nào -> tiến độ 0%
        @Test
        void create_noCheckIns_progressIsZero() {
            User owner = user(1L);
            UserPlan mapped = UserPlan.builder().planHotspots(new ArrayList<>()).build();
            when(userService.getCurrentUser()).thenReturn(owner);
            when(planMapper.toEntity(any(CreateCustomPlanRequest.class))).thenReturn(mapped);
            when(planMapper.toEntity(any(PlanStopRequest.class))).thenAnswer(inv -> new PlanHotspot());
            when(hotspotService.getById(anyLong())).thenAnswer(inv -> hotspot(inv.getArgument(0)));
            when(userPlanRepository.save(any(UserPlan.class))).thenAnswer(inv -> {
                UserPlan saved = inv.getArgument(0);
                saved.setUser(owner);
                return saved;
            });
            when(userHotspotProgressRepository.existsByUser_UserIdAndHotspot_HotspotId(
                    anyLong(), anyLong())).thenReturn(false);
            stubMapperResponse(2, 10L, 11L);

            UserPlanResponse result = userPlanService.create(createRequest(List.of(10L, 11L)));

            assertEquals(0, result.getCompletedStops());
            assertEquals(0.0, result.getProgressPercentage());
        }

        // UTCID03 - Normal: check-in 1/2 điểm -> tiến độ 50%
        @Test
        void create_halfCheckedIn_progressIsFiftyPercent() {
            User owner = user(1L);
            UserPlan mapped = UserPlan.builder().user(owner).planHotspots(new ArrayList<>()).build();
            when(userService.getCurrentUser()).thenReturn(owner);
            when(planMapper.toEntity(any(CreateCustomPlanRequest.class))).thenReturn(mapped);
            when(planMapper.toEntity(any(PlanStopRequest.class))).thenAnswer(inv -> new PlanHotspot());
            when(hotspotService.getById(anyLong())).thenAnswer(inv -> hotspot(inv.getArgument(0)));
            when(userPlanRepository.save(any(UserPlan.class))).thenAnswer(inv -> inv.getArgument(0));
            when(userHotspotProgressRepository.existsByUser_UserIdAndHotspot_HotspotId(1L, 10L))
                    .thenReturn(true);
            when(userHotspotProgressRepository.existsByUser_UserIdAndHotspot_HotspotId(1L, 11L))
                    .thenReturn(false);
            stubMapperResponse(2, 10L, 11L);

            UserPlanResponse result = userPlanService.create(createRequest(List.of(10L, 11L)));

            assertEquals(1, result.getCompletedStops());
            assertEquals(50.0, result.getProgressPercentage());
            assertTrue(result.getStops().get(0).getIsCheckedIn());
            assertFalse(result.getStops().get(1).getIsCheckedIn());
        }
    }

    // =====================================================================
    // Function: update
    // =====================================================================
    @Nested
    @DisplayName("update")
    class UpdateTest {

        // UTCID01 - Abnormal: kế hoạch không tồn tại
        @Test
        void update_planNotFound_throwsPlanNotFound() {
            when(userPlanRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userPlanService.update(1L, createRequest(List.of(10L))));

            assertEquals("Kế hoạch không tồn tại", ex.getMessage());
        }

        // UTCID02 - Abnormal: sửa kế hoạch của người khác
        @Test
        void update_notOwner_throwsNoPermission() {
            when(userPlanRepository.findById(1L))
                    .thenReturn(Optional.of(plan(1L, user(1L), PlanStatus.READY, 3)));
            when(userService.getCurrentUser()).thenReturn(user(2L));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userPlanService.update(1L, createRequest(List.of(10L))));

            assertEquals("Bạn không có quyền truy cập kế hoạch này", ex.getMessage());
        }

        // UTCID03 - Abnormal: kế hoạch đã bắt đầu -> không cho sửa
        @Test
        void update_startedPlan_throwsCannotEdit() {
            User owner = user(1L);
            when(userPlanRepository.findById(1L))
                    .thenReturn(Optional.of(plan(1L, owner, PlanStatus.STARTED, 3)));
            when(userService.getCurrentUser()).thenReturn(owner);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userPlanService.update(1L, createRequest(List.of(10L))));

            assertEquals("Kế hoạch đã bắt đầu hành trình, không thể chỉnh sửa", ex.getMessage());
            verify(userPlanRepository, never()).save(any());
        }

        // UTCID04 - Normal: sửa kế hoạch chưa bắt đầu -> thay toàn bộ điểm dừng cũ
        @Test
        void update_readyPlan_replacesAllStops() {
            User owner = user(1L);
            UserPlan target = plan(1L, owner, PlanStatus.READY, 3);
            when(userPlanRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(owner);
            when(planMapper.toEntity(any(PlanStopRequest.class))).thenAnswer(inv -> new PlanHotspot());
            when(hotspotService.getById(anyLong())).thenAnswer(inv -> hotspot(inv.getArgument(0)));
            when(userPlanRepository.save(any(UserPlan.class))).thenAnswer(inv -> inv.getArgument(0));
            stubMapperResponse(2, 20L, 21L);

            CreateCustomPlanRequest request = createRequest(List.of(20L, 21L));
            userPlanService.update(1L, request);

            verify(planMapper).updateFromRequest(target, request);
            assertEquals(2, target.getTotalStops());
            assertEquals(2, target.getPlanHotspots().size());
        }
    }

    // =====================================================================
    // Function: delete / getById
    // =====================================================================
    @Nested
    @DisplayName("delete")
    class DeleteTest {

        // UTCID01 - Abnormal: kế hoạch không tồn tại
        @Test
        void delete_planNotFound_throwsPlanNotFound() {
            when(userPlanRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userPlanService.delete(1L));

            assertEquals("Kế hoạch không tồn tại", ex.getMessage());
        }

        // UTCID02 - Abnormal: xóa kế hoạch của người khác
        @Test
        void delete_notOwner_throwsNoPermission() {
            when(userPlanRepository.findById(1L))
                    .thenReturn(Optional.of(plan(1L, user(1L), PlanStatus.READY, 3)));
            when(userService.getCurrentUser()).thenReturn(user(2L));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userPlanService.delete(1L));

            assertEquals("Bạn không có quyền truy cập kế hoạch này", ex.getMessage());
            verify(userPlanRepository, never()).save(any());
        }

        // UTCID03 - Normal: xóa mềm -> đánh dấu isDeleted và ghi thời điểm xóa
        @Test
        void delete_owner_marksAsDeletedWithTimestamp() {
            User owner = user(1L);
            UserPlan target = plan(1L, owner, PlanStatus.READY, 3);
            when(userPlanRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(owner);

            userPlanService.delete(1L);

            assertTrue(target.getIsDeleted());
            assertNotNull(target.getDeletedAt());
            verify(userPlanRepository).save(target);
        }

        // UTCID04 - Abnormal: xem chi tiết kế hoạch của người khác -> cũng bị chặn
        @Test
        void getById_notOwner_throwsNoPermission() {
            when(userPlanRepository.findById(1L))
                    .thenReturn(Optional.of(plan(1L, user(1L), PlanStatus.READY, 3)));
            when(userService.getCurrentUser()).thenReturn(user(2L));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userPlanService.getById(1L));

            assertEquals("Bạn không có quyền truy cập kế hoạch này", ex.getMessage());
        }
    }
}
