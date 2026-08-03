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
import org.sep490.backend.common.utils.SpatialUtils;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.exploration.dto.request.UserHotspotProgressRequest;
import org.sep490.backend.module.exploration.dto.response.UserHotspotProgressResponse;
import org.sep490.backend.module.exploration.entity.UserHotspotProgress;
import org.sep490.backend.module.exploration.event.CheckInCompletedEvent;
import org.sep490.backend.module.exploration.repository.UserHotspotProgressRepository;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test cho luồng CHECK-IN HOTSPOT (Exploration).
 * Ngưỡng bán kính check-in: 50m (biên tại đúng 50m).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserHotspotProgressServiceImplTest {

    @Mock private UserHotspotProgressRepository userHotspotProgressRepository;
    @Mock private HotspotService hotspotService;
    @Mock private UserService userService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private UserHotspotProgressServiceImpl service;

    // ~0.000450 độ kinh/vĩ tuyến tại Hà Nội xấp xỉ 50m theo chiều bắc-nam
    private static final double HOTSPOT_LAT = 21.020000;
    private static final double HOTSPOT_LNG = 105.850000;

    private User user(Long id) {
        User u = new User();
        u.setUserId(id);
        return u;
    }

    private Hotspot hotspot() {
        Hotspot h = new Hotspot();
        h.setHotspotId(100L);
        h.setHotspotName("Hồ Gươm");
        h.setLocation(SpatialUtils.fromCoordinates(HOTSPOT_LNG, HOTSPOT_LAT));
        h.setPoint(10L);
        h.setXp(20L);
        return h;
    }

    private UserHotspotProgressRequest request(double lat, double lng) {
        UserHotspotProgressRequest request = new UserHotspotProgressRequest();
        request.setHotspotId(100L);
        request.setLatitude(lat);
        request.setLongitude(lng);
        return request;
    }

    // =====================================================================
    // Function: checkIn
    // =====================================================================
    @Nested
    @DisplayName("checkIn")
    class CheckInTest {

        // UTCID01 - Abnormal: hotspot không tồn tại
        @Test
        void checkIn_hotspotNotFound_throwsNotFound() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(hotspotService.getById(100L)).thenThrow(new BusinessException("Không tìm thấy Hotspot"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.checkIn(request(HOTSPOT_LAT, HOTSPOT_LNG)));

            assertEquals("Không tìm thấy Hotspot", ex.getMessage());
        }

        // UTCID02 - Abnormal: đã check-in tại hotspot này trước đó
        @Test
        void checkIn_alreadyCheckedIn_throwsAlreadyCheckedIn() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(hotspotService.getById(100L)).thenReturn(hotspot());
            when(userHotspotProgressRepository.existsByUser_UserIdAndHotspot_HotspotId(1L, 100L)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.checkIn(request(HOTSPOT_LAT, HOTSPOT_LNG)));

            assertEquals("Bạn đã check-in tại hotspot này trước đó", ex.getMessage());
        }

        // UTCID03 - Abnormal: đứng ngoài bán kính 50m (cách ~100m)
        @Test
        void checkIn_outsideRadius_throwsOutsideCheckInZone() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(hotspotService.getById(100L)).thenReturn(hotspot());
            when(userHotspotProgressRepository.existsByUser_UserIdAndHotspot_HotspotId(1L, 100L)).thenReturn(false);

            // ~0.0009 độ vĩ tuyến ~ 100m
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.checkIn(request(HOTSPOT_LAT + 0.0009, HOTSPOT_LNG)));

            assertEquals("Bạn đang ở ngoài vùng check-in. Hãy di chuyển vào bán kính 50m để check-in",
                    ex.getMessage());
        }

        // UTCID04 - Boundary: đứng sát mép bán kính (~45m, ngay dưới ngưỡng 50m) -> thành công
        @Test
        void checkIn_justInsideRadius_succeeds() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(hotspotService.getById(100L)).thenReturn(hotspot());
            when(userHotspotProgressRepository.existsByUser_UserIdAndHotspot_HotspotId(1L, 100L)).thenReturn(false);
            when(userHotspotProgressRepository.save(any(UserHotspotProgress.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // ~0.0004 độ vĩ tuyến ~ 44.5m < 50m
            UserHotspotProgressResponse response = service.checkIn(request(HOTSPOT_LAT + 0.0004, HOTSPOT_LNG));

            assertNotNull(response);
            assertTrue(response.getIsCheckedIn());
        }

        // UTCID05 - Normal: đứng ngay tại hotspot -> check-in thành công, cộng điểm/XP, bắn event
        @Test
        void checkIn_atHotspot_succeedsAndPublishesEvent() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(hotspotService.getById(100L)).thenReturn(hotspot());
            when(userHotspotProgressRepository.existsByUser_UserIdAndHotspot_HotspotId(1L, 100L)).thenReturn(false);
            when(userHotspotProgressRepository.save(any(UserHotspotProgress.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            UserHotspotProgressResponse response = service.checkIn(request(HOTSPOT_LAT, HOTSPOT_LNG));

            assertNotNull(response);
            assertTrue(response.getIsCheckedIn());
            assertEquals(10, response.getTotalPointEarned());
            assertEquals(20, response.getTotalXpEarned());
            verify(eventPublisher).publishEvent(any(CheckInCompletedEvent.class));
        }
    }
}
