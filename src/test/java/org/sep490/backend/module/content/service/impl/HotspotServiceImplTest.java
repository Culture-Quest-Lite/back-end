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
import org.sep490.backend.module.user.entity.enumeration.UserRole;
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

    private static User aCurator() {
        User user = new User();
        user.setUserId(1L);
        user.setUsername("curator01");
        user.setDisplayName("Nguyen Thu Ha");
        user.setEmail("curator01@culturequest.vn");
        user.setRole(UserRole.CURATOR);
        return user;
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
            hotspot.setHotspotId(1L);
            hotspot.setHotspotName("Ho Guom");
            when(hotspotMapper.toEntity(request)).thenReturn(hotspot);
            when(userService.getCurrentUser()).thenReturn(aCurator());
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
    // =====================================================================
    // Function: getById / updateStatus (Hotspot)
    // =====================================================================
    @Nested
    @DisplayName("getById")
    class GetByIdTest {

        // UTCID01 - Abnormal: hotspot không tồn tại
        @Test
        void getById_notFound_throwsHotspotNotExist() {
            when(hotspotRepository.findById(99L)).thenReturn(java.util.Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> hotspotService.getById(99L));

            assertEquals("Không tìm thấy Hotspot", ex.getMessage());
        }

        // UTCID02 - Normal: trả về entity khi tồn tại
        @Test
        void getById_existing_returnsEntity() {
            Hotspot hotspot = new Hotspot();
            hotspot.setHotspotId(1L);
            hotspot.setHotspotName("Ho Guom");
            when(hotspotRepository.findById(1L)).thenReturn(java.util.Optional.of(hotspot));

            assertSame(hotspot, hotspotService.getById(1L));
        }

        // UTCID03 - Normal: duyệt hotspot -> chuyển sang PUBLISHED
        @Test
        void updateStatus_toPublished_updatesStatus() {
            Hotspot hotspot = new Hotspot();
            hotspot.setHotspotId(1L);
            hotspot.setHotspotName("Ho Guom");
            hotspot.setStatus(ContentStatus.DRAFT);
            when(hotspotRepository.findById(1L)).thenReturn(java.util.Optional.of(hotspot));
            when(hotspotRepository.save(any(Hotspot.class))).thenAnswer(inv -> inv.getArgument(0));
            when(hotspotMapper.toResponse(any(Hotspot.class))).thenReturn(new HotspotResponse());

            hotspotService.updateStatus(1L, ContentStatus.PUBLISHED);

            assertEquals(ContentStatus.PUBLISHED, hotspot.getStatus());
            verify(hotspotRepository).save(hotspot);
        }

        // UTCID04 - Abnormal: đổi trạng thái hotspot không tồn tại -> báo lỗi
        @Test
        void updateStatus_notFound_throwsHotspotNotExist() {
            when(hotspotRepository.findById(99L)).thenReturn(java.util.Optional.empty());

            assertThrows(BusinessException.class,
                    () -> hotspotService.updateStatus(99L, ContentStatus.PUBLISHED));
        }
    }

    // =====================================================================
    // Function: delete (Hotspot)
    // =====================================================================
    @Nested
    @DisplayName("deleteHotspot")
    class DeleteHotspotTest {

        private User curator(Long userId) {
            User user = new User();
            user.setUserId(userId);
            user.setUsername(userId == 1L ? "curator01" : "curator02");
            user.setDisplayName(userId == 1L ? "Nguyen Thu Ha" : "Pham Van Long");
            user.setEmail(userId == 1L ? "curator01@culturequest.vn" : "curator02@culturequest.vn");
            user.setRole(UserRole.CURATOR);
            return user;
        }

        // UTCID01 - Abnormal: người dùng không phải CURATOR -> không có quyền xóa
        @Test
        void deleteHotspot_notCurator_throwsNoPermission() {
            User explorer = new User();
            explorer.setUserId(1L);
            explorer.setUsername("traveler01");
            explorer.setDisplayName("Tran Minh Anh");
            explorer.setRole(UserRole.EXPLORER);
            when(userService.getCurrentUser()).thenReturn(explorer);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> hotspotService.delete(1L));

            assertEquals("Bạn không có quyền xóa Hotspot", ex.getMessage());
            verify(hotspotRepository, never()).save(any());
        }

        // UTCID02 - Abnormal: curator không phải người tạo hotspot -> chặn xóa
        @Test
        void deleteHotspot_notOwner_throwsNoPermissionOnThisHotspot() {
            User owner = curator(1L);
            User other = curator(2L);
            Hotspot hotspot = new Hotspot();
            hotspot.setHotspotId(5L);
            hotspot.setHotspotName("Ho Guom");
            hotspot.setCreatedBy(owner);

            when(userService.getCurrentUser()).thenReturn(other);
            when(hotspotRepository.findById(5L)).thenReturn(java.util.Optional.of(hotspot));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> hotspotService.delete(5L));

            assertEquals("Bạn không có quyền xóa Hotspot này", ex.getMessage());
        }

        // UTCID03 - Abnormal: hotspot còn story đã xuất bản -> không được xóa
        @Test
        void deleteHotspot_hasPublishedStories_throwsHasPublishedStories() {
            User owner = curator(1L);
            Hotspot hotspot = new Hotspot();
            hotspot.setHotspotId(5L);
            hotspot.setHotspotName("Ho Guom");
            hotspot.setCreatedBy(owner);
            org.sep490.backend.module.content.entity.Story story =
                    new org.sep490.backend.module.content.entity.Story();
            story.setStoryId(30L);
            story.setStatus(ContentStatus.PUBLISHED);

            when(userService.getCurrentUser()).thenReturn(owner);
            when(hotspotRepository.findById(5L)).thenReturn(java.util.Optional.of(hotspot));
            when(storyRepository.findByHotspot_HotspotIdAndStatus(5L, ContentStatus.PUBLISHED))
                    .thenReturn(List.of(story));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> hotspotService.delete(5L));

            assertEquals("Không thể xóa Hotspot này vì có các Story đã được xuất bản: [30]",
                    ex.getMessage());
            verify(hotspotRepository, never()).save(any());
        }

        // UTCID04 - Normal: chủ sở hữu, không còn story xuất bản -> xóa mềm
        @Test
        void deleteHotspot_ownerWithoutPublishedStories_softDeletes() {
            User owner = curator(1L);
            Hotspot hotspot = new Hotspot();
            hotspot.setHotspotId(5L);
            hotspot.setHotspotName("Ho Guom");
            hotspot.setCreatedBy(owner);
            hotspot.setStatus(ContentStatus.PUBLISHED);

            when(userService.getCurrentUser()).thenReturn(owner);
            when(hotspotRepository.findById(5L)).thenReturn(java.util.Optional.of(hotspot));
            when(storyRepository.findByHotspot_HotspotIdAndStatus(5L, ContentStatus.PUBLISHED))
                    .thenReturn(List.of());

            hotspotService.delete(5L);

            assertEquals(ContentStatus.DELETED, hotspot.getStatus());
            verify(hotspotRepository).save(hotspot);
        }

        // UTCID05 - Normal: xóa xong phải xóa cache truy vấn lân cận
        @Test
        void deleteHotspot_evictsNearbyGeoCache() {
            User owner = curator(1L);
            Hotspot hotspot = new Hotspot();
            hotspot.setHotspotId(5L);
            hotspot.setHotspotName("Ho Guom");
            hotspot.setCreatedBy(owner);

            when(userService.getCurrentUser()).thenReturn(owner);
            when(hotspotRepository.findById(5L)).thenReturn(java.util.Optional.of(hotspot));
            when(storyRepository.findByHotspot_HotspotIdAndStatus(anyLong(), any()))
                    .thenReturn(List.of());

            hotspotService.delete(5L);

            verify(geoQueryService).evictNearby();
        }
    }
}
