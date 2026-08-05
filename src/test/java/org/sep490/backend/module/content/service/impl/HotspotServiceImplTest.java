package org.sep490.backend.module.content.service.impl;

import org.sep490.backend.module.content.service.inter.GeoQueryService;

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
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.dto.request.HotspotRequest;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.sep490.backend.module.content.mapper.HotspotMapper;
import org.sep490.backend.module.content.mapper.StoryMapper;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.content.service.inter.MediaService;
import org.sep490.backend.module.exploration.repository.UserHotspotProgressRepository;
import org.sep490.backend.module.user.service.UserService;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho luồng QUẢN LÝ HOTSPOT (Content).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HotspotServiceImplTest {

    @Mock private HotspotRepository hotspotRepository;
    @Mock private HotspotMapper hotspotMapper;
    @Mock private UserService userService;
    @Mock private StoryRepository storyRepository;
    @Mock private StoryMapper storyMapper;
    @Mock private MediaService mediaService;
    @Mock private UserHotspotProgressRepository userHotspotProgressRepository;

    @Mock private RatingSummaryService ratingSummaryService;
    @Mock private CheckInStatusService checkInStatusService;
    @Mock private GeoQueryService geoQueryService;
    @InjectMocks private HotspotServiceImpl hotspotService;

    @org.junit.jupiter.api.BeforeEach
    void setUpAppliers() {
        // Trong code thật các applier trả về chính đối số sau khi gán rating/check-in
        when(ratingSummaryService.applyToHotspot(any(HotspotResponse.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(checkInStatusService.apply(any(HotspotResponse.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // =====================================================================
    // Function: create (Hotspot)
    // =====================================================================
    @Nested
    @DisplayName("createHotspot")
    class CreateHotspotTest {

        private HotspotRequest hotspotRequest() {
            HotspotRequest request = new HotspotRequest();
            request.setLatitude(21.02);
            request.setLongitude(105.85);
            request.setStartTime(LocalTime.of(8, 0));
            request.setEndTime(LocalTime.of(17, 0));
            request.setEstimatedDurationMin(30L);
            request.setEstimatedDurationMax(90L);
            return request;
        }

        // UTCID01 - Abnormal: tọa độ không thuộc lãnh thổ Việt Nam
        @Test
        void createHotspot_locationOutsideVietnam_throwsInvalidLocation() {
            HotspotRequest request = hotspotRequest();
            when(geoQueryService.isLocationInVietnam(105.85, 21.02)).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> hotspotService.create(request));

            assertEquals("Tọa độ của Hotspot phải thuộc lãnh thổ Việt Nam", ex.getMessage());
        }

        // UTCID02 - Abnormal: thời gian kết thúc trước thời gian bắt đầu
        @Test
        void createHotspot_endBeforeStart_throwsInvalidEndTime() {
            HotspotRequest request = hotspotRequest();
            request.setStartTime(LocalTime.of(17, 0));
            request.setEndTime(LocalTime.of(8, 0));
            when(geoQueryService.isLocationInVietnam(any(), any())).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> hotspotService.create(request));

            assertEquals("Thời gian kết thúc không hợp lệ", ex.getMessage());
        }

        // UTCID03 - Abnormal: thời gian tham quan tối đa nhỏ hơn tối thiểu
        @Test
        void createHotspot_maxDurationLessThanMin_throwsInvalidDuration() {
            HotspotRequest request = hotspotRequest();
            request.setEstimatedDurationMin(90L);
            request.setEstimatedDurationMax(30L);
            when(geoQueryService.isLocationInVietnam(any(), any())).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> hotspotService.create(request));

            assertEquals("Thời gian tham quan dự kiến không hợp lệ", ex.getMessage());
        }

        // UTCID04 - Normal: tạo hotspot thành công, trạng thái DRAFT
        @Test
        void createHotspot_valid_createsDraftHotspot() {
            HotspotRequest request = hotspotRequest();
            when(geoQueryService.isLocationInVietnam(any(), any())).thenReturn(true);
            Hotspot hotspot = new Hotspot();
            when(hotspotMapper.toEntity(request)).thenReturn(hotspot);
            when(userService.getCurrentUser()).thenReturn(new User());
            when(hotspotRepository.save(any(Hotspot.class))).thenAnswer(inv -> inv.getArgument(0));
            when(hotspotMapper.toResponse(any(Hotspot.class))).thenReturn(new HotspotResponse());
            when(storyRepository.findByHotspotOrderedByIndex(anyLong())).thenReturn(List.of());

            HotspotResponse response = hotspotService.create(request);

            assertNotNull(response);
            assertEquals(ContentStatus.DRAFT, hotspot.getStatus());
            verify(hotspotRepository).save(hotspot);
        }
    }

    // =====================================================================
    // Function: getNearbyHotspots
    // =====================================================================
    @Nested
    @DisplayName("getNearbyHotspots")
    class GetNearbyHotspotsTest {

        // UTCID01 - Abnormal: thiếu vĩ độ
        @Test
        void getNearbyHotspots_nullLatitude_throwsMissingCoordinates() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> hotspotService.getNearbyHotspots(null, 105.85, 1000.0));

            assertEquals("Tung độ và hoành độ không được để trống", ex.getMessage());
        }

        // UTCID02 - Abnormal: thiếu kinh độ
        @Test
        void getNearbyHotspots_nullLongitude_throwsMissingCoordinates() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> hotspotService.getNearbyHotspots(21.02, null, 1000.0));

            assertEquals("Tung độ và hoành độ không được để trống", ex.getMessage());
        }

        // UTCID03 - Boundary: khoảng cách = 0 (chạm ngưỡng "phải > 0")
        @Test
        void getNearbyHotspots_zeroDistance_throwsInvalidDistance() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> hotspotService.getNearbyHotspots(21.02, 105.85, 0.0));

            assertEquals("Khoảng cách phải lớn hơn 0", ex.getMessage());
        }

        // UTCID04 - Normal: tọa độ và khoảng cách hợp lệ -> trả về danh sách
        @Test
        void getNearbyHotspots_valid_returnsList() {
            when(hotspotRepository.findNearbyHotspotsWithStatus(
                    anyDouble(), anyDouble(), anyDouble(), anyString())).thenReturn(List.of());

            List<HotspotResponse> result = hotspotService.getNearbyHotspots(21.02, 105.85, 1000.0);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }
}
