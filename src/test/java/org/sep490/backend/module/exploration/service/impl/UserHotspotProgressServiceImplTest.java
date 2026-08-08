package org.sep490.backend.module.exploration.service.impl;

import org.sep490.backend.module.content.service.inter.CheckInStatusService;

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
import org.sep490.backend.module.content.repository.HotspotRepository;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserHotspotProgressServiceImplTest {

    @Mock private UserHotspotProgressRepository userHotspotProgressRepository;
    @Mock private HotspotService hotspotService;
    @Mock private HotspotRepository hotspotRepository;
    @Mock private UserService userService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock private CheckInStatusService checkInStatusService;
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

            // Thông báo giờ nêu khoảng cách thật và ngưỡng thật của từng hotspot
            assertTrue(ex.getMessage().contains("cần vào trong phạm vi"), ex.getMessage());
        }

        // UTCID06 - Normal: hotspot khai báo bán kính rộng (khu du lịch lớn)
        // -> đứng cách tâm 500m vẫn check-in được
        @Test
        void checkIn_customLargeRadius_succeedsFarFromCenter() {
            Hotspot large = hotspot();
            large.setCheckInRadius(800);

            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(hotspotService.getById(100L)).thenReturn(large);
            when(userHotspotProgressRepository.existsByUser_UserIdAndHotspot_HotspotId(1L, 100L)).thenReturn(false);
            when(userHotspotProgressRepository.save(any(UserHotspotProgress.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // ~0.0045 độ vĩ tuyến ~ 500m -> trước đây bị từ chối, giờ hợp lệ
            UserHotspotProgressResponse response = service.checkIn(request(HOTSPOT_LAT + 0.0045, HOTSPOT_LNG));

            assertNotNull(response);
            assertTrue(response.getIsCheckedIn());
        }

        // UTCID07 - Normal: sai số GPS nới ngưỡng cho người đứng sát mép vùng
        @Test
        void checkIn_gpsAccuracyExtendsThreshold_succeeds() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(hotspotService.getById(100L)).thenReturn(hotspot());
            when(userHotspotProgressRepository.existsByUser_UserIdAndHotspot_HotspotId(1L, 100L)).thenReturn(false);
            when(userHotspotProgressRepository.save(any(UserHotspotProgress.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // ~78m: ngoài bán kính 50m nhưng accuracy 40m nới ngưỡng lên 90m
            UserHotspotProgressRequest request = request(HOTSPOT_LAT + 0.0007, HOTSPOT_LNG);
            request.setAccuracy(40.0);

            UserHotspotProgressResponse response = service.checkIn(request);

            assertNotNull(response);
            assertTrue(response.getIsCheckedIn());
        }

        // UTCID08 - Abnormal: accuracy khai khống vẫn bị chặn bởi trần 100m
        @Test
        void checkIn_spoofedHugeAccuracy_stillRejected() {
            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(hotspotService.getById(100L)).thenReturn(hotspot());
            when(userHotspotProgressRepository.existsByUser_UserIdAndHotspot_HotspotId(1L, 100L)).thenReturn(false);

            // ~5km với accuracy 99999 -> ngưỡng tối đa chỉ là 50 + 100 = 150m
            UserHotspotProgressRequest request = request(HOTSPOT_LAT + 0.045, HOTSPOT_LNG);
            request.setAccuracy(99999.0);

            assertThrows(BusinessException.class, () -> service.checkIn(request));
        }

        // UTCID09 - Abnormal: hotspot thiếu toạ độ không được âm thầm cho qua
        // (SpatialUtils trả 0.0 khi Point null nên trước đây sẽ luôn thành công)
        @Test
        void checkIn_hotspotWithoutLocation_throws() {
            Hotspot broken = hotspot();
            broken.setLocation(null);

            when(userService.getCurrentUser()).thenReturn(user(1L));
            when(hotspotService.getById(100L)).thenReturn(broken);
            when(userHotspotProgressRepository.existsByUser_UserIdAndHotspot_HotspotId(1L, 100L)).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.checkIn(request(HOTSPOT_LAT, HOTSPOT_LNG)));

            assertEquals("Hotspot chưa có toạ độ hợp lệ", ex.getMessage());
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
