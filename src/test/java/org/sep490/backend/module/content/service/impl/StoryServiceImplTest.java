package org.sep490.backend.module.content.service.impl;

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
import org.sep490.backend.module.content.dto.request.StoryRequest;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.sep490.backend.module.content.entity.enumeration.TagStatus;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.sep490.backend.module.content.mapper.StoryMapper;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.content.repository.TagRepository;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.content.service.inter.MediaService;
import org.sep490.backend.module.content.dto.response.MediaResponse;
import org.sep490.backend.module.content.entity.enumeration.MediaTargetType;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho luồng QUẢN LÝ STORY (Content).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoryServiceImplTest {

    @Mock private StoryRepository storyRepository;
    @Mock private StoryMapper storyMapper;
    @Mock private UserService userService;
    @Mock private MediaService mediaService;
    @Mock private TagRepository tagRepository;
    @Mock private HotspotService hotspotService;

    @Mock private RatingSummaryService ratingSummaryService;
    @InjectMocks private StoryServiceImpl storyService;

    // ---------------------------------------------------------------
    // Du lieu test dung chung - moi entity deu co gia tri cu the
    // ---------------------------------------------------------------
    private static Tag tagLichSu() {
        Tag tag = new Tag();
        tag.setTagId(1L);
        tag.setTagName("Lich su");
        tag.setTagStatus(TagStatus.ACTIVE);
        return tag;
    }

    private static Hotspot hoGuom() {
        Hotspot hotspot = new Hotspot();
        hotspot.setHotspotId(2L);
        hotspot.setHotspotName("Ho Guom");
        hotspot.setStatus(ContentStatus.PUBLISHED);
        return hotspot;
    }

    private static User curator() {
        User user = new User();
        user.setUserId(1L);
        user.setUsername("curator01");
        user.setDisplayName("Nguyen Thu Ha");
        user.setEmail("curator01@culturequest.vn");
        user.setRole(UserRole.CURATOR);
        return user;
    }

    private static Story storyOf(Long storyId, String title, ContentStatus status) {
        Story story = new Story();
        story.setStoryId(storyId);
        story.setTitle(title);
        story.setContent("Noi dung cot truyen...");
        story.setStatus(status);
        return story;
    }

    @org.junit.jupiter.api.BeforeEach
    void setUpAppliers() {
        // Trong code thật các applier trả về chính đối số sau khi gán rating/check-in
        when(ratingSummaryService.applyToStory(any(StoryResponse.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // =====================================================================
    // Function: create (Story)
    // =====================================================================
    @Nested
    @DisplayName("createStory")
    class CreateStoryTest {

        private StoryRequest storyRequest() {
            StoryRequest request = new StoryRequest();
            request.setTagId(1L);
            request.setHotspotId(2L);
            request.setTitle("Sự tích Hồ Gươm");
            request.setContent("Nội dung cốt truyện...");
            return request;
        }

        // UTCID01 - Abnormal: tag không tồn tại
        @Test
        void createStory_tagNotFound_throwsTagNotExist() {
            StoryRequest request = storyRequest();
            when(tagRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> storyService.create(request));

            assertEquals("Tag không tồn tại với ID: 1", ex.getMessage());
        }

        // UTCID02 - Abnormal: hotspot không tồn tại
        @Test
        void createStory_hotspotNotFound_throwsHotspotNotExist() {
            StoryRequest request = storyRequest();
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tagLichSu()));
            when(hotspotService.getById(2L)).thenThrow(new BusinessException("Không tìm thấy Hotspot"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> storyService.create(request));

            assertEquals("Không tìm thấy Hotspot", ex.getMessage());
        }

        // UTCID03 - Normal: tạo story thành công, trạng thái DRAFT
        @Test
        void createStory_valid_createsDraftStory() {
            StoryRequest request = storyRequest();
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tagLichSu()));
            when(hotspotService.getById(2L)).thenReturn(hoGuom());
            Story story = storyOf(10L, "Su tich Ho Guom", ContentStatus.DRAFT);
            when(storyMapper.toEntity(request)).thenReturn(story);
            when(userService.getCurrentUser()).thenReturn(curator());
            when(storyRepository.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));
            when(storyMapper.toResponse(any(Story.class))).thenReturn(new StoryResponse());

            StoryResponse response = storyService.create(request);

            assertNotNull(response);
            assertEquals(ContentStatus.DRAFT, story.getStatus());
            verify(storyRepository).save(story);
        }

        // UTCID04 - Normal: có kèm file media -> upload và gắn vào response
        @Test
        void createStory_withMediaFiles_uploadsAndAttaches() throws Exception {
            StoryRequest request = storyRequest();
            request.setFiles(new MultipartFile[]{
                    new MockMultipartFile("files", "ho-guom.jpg", "image/jpeg", new byte[512])});

            when(tagRepository.findById(1L)).thenReturn(Optional.of(tagLichSu()));
            when(hotspotService.getById(2L)).thenReturn(hoGuom());
            Story story = storyOf(10L, "Su tich Ho Guom", ContentStatus.DRAFT);
            story.setStoryId(10L);
            when(storyMapper.toEntity(request)).thenReturn(story);
            when(userService.getCurrentUser()).thenReturn(curator());
            when(storyRepository.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));
            when(storyMapper.toResponse(any(Story.class))).thenReturn(new StoryResponse());
            when(mediaService.uploadAndSaveMedias(any(), eq(MediaTargetType.STORY), eq(10L)))
                    .thenReturn(List.of(new MediaResponse()));

            StoryResponse response = storyService.create(request);

            assertEquals(1, response.getMedias().size());
            verify(mediaService).uploadAndSaveMedias(any(), eq(MediaTargetType.STORY), eq(10L));
        }

        // UTCID05 - Abnormal: upload media lỗi -> đổi sang lỗi nghiệp vụ để rollback transaction
        @Test
        void createStory_mediaUploadFails_throwsBusinessException() throws Exception {
            StoryRequest request = storyRequest();
            request.setFiles(new MultipartFile[]{
                    new MockMultipartFile("files", "ho-guom.jpg", "image/jpeg", new byte[512])});

            when(tagRepository.findById(1L)).thenReturn(Optional.of(tagLichSu()));
            when(hotspotService.getById(2L)).thenReturn(hoGuom());
            Story story = storyOf(10L, "Su tich Ho Guom", ContentStatus.DRAFT);
            story.setStoryId(10L);
            when(storyMapper.toEntity(request)).thenReturn(story);
            when(userService.getCurrentUser()).thenReturn(curator());
            when(storyRepository.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));
            when(storyMapper.toResponse(any(Story.class))).thenReturn(new StoryResponse());
            when(mediaService.uploadAndSaveMedias(any(), any(), anyLong()))
                    .thenThrow(new IOException("S3 timeout"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> storyService.create(request));

            assertEquals("Lỗi tải lên media: S3 timeout", ex.getMessage());
        }
    }

    // =====================================================================
    // Function: update (Story)
    // =====================================================================
    @Nested
    @DisplayName("updateStory")
    class UpdateStoryTest {

        private StoryRequest storyRequest() {
            StoryRequest request = new StoryRequest();
            request.setTagId(1L);
            request.setHotspotId(2L);
            request.setTitle("Sự tích Hồ Gươm");
            return request;
        }

        // UTCID01 - Abnormal: story không tồn tại
        @Test
        void updateStory_storyNotFound_throwsNotFound() {
            when(storyRepository.findById(99L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> storyService.update(99L, storyRequest()));

            assertEquals("Không tìm thấy câu chuyện với id: 99", ex.getMessage());
        }

        // UTCID02 - Abnormal: tag mới không tồn tại -> chặn cập nhật
        @Test
        void updateStory_tagNotFound_throwsTagNotExist() {
            when(storyRepository.findById(10L)).thenReturn(Optional.of(storyOf(10L, "Su tich Ho Guom", ContentStatus.DRAFT)));
            when(tagRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> storyService.update(10L, storyRequest()));

            assertEquals("Tag không tồn tại với ID: 1", ex.getMessage());
            verify(storyRepository, never()).save(any());
        }

        // UTCID03 - Normal: cập nhật hợp lệ -> gán lại tag/hotspot và lưu
        @Test
        void updateStory_valid_updatesTagAndHotspot() {
            Story story = storyOf(10L, "Su tich Ho Guom", ContentStatus.DRAFT);
            story.setStoryId(10L);
            Tag newTag = tagLichSu();
            Hotspot newHotspot = hoGuom();

            when(storyRepository.findById(10L)).thenReturn(Optional.of(story));
            when(tagRepository.findById(1L)).thenReturn(Optional.of(newTag));
            when(hotspotService.getById(2L)).thenReturn(newHotspot);
            when(storyRepository.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));
            when(storyMapper.toResponse(any(Story.class))).thenReturn(new StoryResponse());

            StoryRequest request = storyRequest();
            storyService.update(10L, request);

            verify(storyMapper).updateFromRequest(story, request);
            assertSame(newTag, story.getTag());
            assertSame(newHotspot, story.getHotspot());
            verify(storyRepository).save(story);
        }

        // UTCID04 - Boundary: không gửi file mới -> không gọi upload media
        @Test
        void updateStory_withoutFiles_skipsMediaUpload() throws Exception {
            Story story = storyOf(10L, "Su tich Ho Guom", ContentStatus.DRAFT);
            story.setStoryId(10L);
            when(storyRepository.findById(10L)).thenReturn(Optional.of(story));
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tagLichSu()));
            when(hotspotService.getById(2L)).thenReturn(hoGuom());
            when(storyRepository.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));
            when(storyMapper.toResponse(any(Story.class))).thenReturn(new StoryResponse());

            storyService.update(10L, storyRequest());

            verify(mediaService, never()).uploadAndSaveMedias(any(), any(), anyLong());
        }
    }

    // =====================================================================
    // Function: delete / getById / updateStatus (Story)
    // =====================================================================
    @Nested
    @DisplayName("deleteStory")
    class DeleteStoryTest {

        // UTCID01 - Abnormal: story không tồn tại
        @Test
        void deleteStory_notFound_throwsNotFound() {
            when(storyRepository.findById(99L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> storyService.delete(99L));

            assertEquals("Không tìm thấy câu chuyện với id: 99", ex.getMessage());
            verify(storyRepository, never()).save(any());
        }

        // UTCID02 - Normal: xóa mềm, chuyển trạng thái DELETED
        @Test
        void deleteStory_existing_softDeletes() {
            Story story = storyOf(10L, "Su tich Ho Guom", ContentStatus.DRAFT);
            story.setStoryId(10L);
            story.setStatus(ContentStatus.PUBLISHED);
            when(storyRepository.findById(10L)).thenReturn(Optional.of(story));

            storyService.delete(10L);

            assertEquals(ContentStatus.DELETED, story.getStatus());
            verify(storyRepository).save(story);
        }

        // UTCID03 - Normal: duyệt story -> chuyển sang PUBLISHED
        @Test
        void updateStatus_toPublished_updatesStatus() {
            Story story = storyOf(10L, "Su tich Ho Guom", ContentStatus.DRAFT);
            story.setStoryId(10L);
            story.setStatus(ContentStatus.DRAFT);
            when(storyRepository.findById(10L)).thenReturn(Optional.of(story));
            when(storyMapper.toResponse(any(Story.class))).thenReturn(new StoryResponse());

            storyService.updateStatus(10L, ContentStatus.PUBLISHED);

            assertEquals(ContentStatus.PUBLISHED, story.getStatus());
            verify(storyRepository).save(story);
        }

        // UTCID04 - Abnormal: đổi trạng thái story không tồn tại -> báo lỗi
        @Test
        void updateStatus_notFound_throwsNotFound() {
            when(storyRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class,
                    () -> storyService.updateStatus(99L, ContentStatus.PUBLISHED));
        }
    }

    // =====================================================================
    // Function: getByHotspot (Story)
    // =====================================================================
    @Nested
    @DisplayName("getByHotspot")
    class GetByHotspotTest {

        // UTCID01 - Abnormal: thiếu cả hotspotId lẫn routeId -> báo lỗi tham số
        @Test
        void getByHotspot_bothParamsNull_throwsMissingParam() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> storyService.getByHotspot(null, null));

            assertEquals("Cần cung cấp ít nhất một trong hai tham số: hotspotId hoặc routeId",
                    ex.getMessage());
        }

        // UTCID02 - Normal: chỉ có hotspotId -> lấy story PUBLISHED của địa điểm
        @Test
        void getByHotspot_onlyHotspotId_queriesByHotspot() {
            when(storyRepository.findByHotspot_HotspotIdAndStatus(2L, ContentStatus.PUBLISHED))
                    .thenReturn(List.of(storyOf(11L, "Truyen thuyet Rua Vang", ContentStatus.PUBLISHED),
                            storyOf(12L, "Le Loi tra guom", ContentStatus.PUBLISHED)));
            when(storyMapper.toResponse(any(Story.class))).thenReturn(new StoryResponse());

            assertEquals(2, storyService.getByHotspot(2L, null).size());
            verify(storyRepository).findByHotspot_HotspotIdAndStatus(2L, ContentStatus.PUBLISHED);
        }

        // UTCID03 - Normal: chỉ có routeId -> lấy story PUBLISHED của tuyến
        @Test
        void getByHotspot_onlyRouteId_queriesByRoute() {
            when(storyRepository.findByRoute_RouteIdAndStatus(5L, ContentStatus.PUBLISHED))
                    .thenReturn(List.of(storyOf(11L, "Truyen thuyet Rua Vang", ContentStatus.PUBLISHED)));
            when(storyMapper.toResponse(any(Story.class))).thenReturn(new StoryResponse());

            assertEquals(1, storyService.getByHotspot(null, 5L).size());
            verify(storyRepository).findByRoute_RouteIdAndStatus(5L, ContentStatus.PUBLISHED);
        }

        // UTCID04 - Normal: có cả hai -> lọc story vừa thuộc tuyến vừa thuộc địa điểm
        @Test
        void getByHotspot_bothParams_queriesByRouteAndHotspot() {
            when(storyRepository.findByRoute_RouteIdAndHotspot_HotspotIdAndStatus(
                    5L, 2L, ContentStatus.PUBLISHED)).thenReturn(List.of(storyOf(11L, "Truyen thuyet Rua Vang", ContentStatus.PUBLISHED)));
            when(storyMapper.toResponse(any(Story.class))).thenReturn(new StoryResponse());

            assertEquals(1, storyService.getByHotspot(2L, 5L).size());
            verify(storyRepository).findByRoute_RouteIdAndHotspot_HotspotIdAndStatus(
                    5L, 2L, ContentStatus.PUBLISHED);
        }

        // UTCID05 - Boundary: địa điểm chưa có story nào -> danh sách rỗng
        @Test
        void getByHotspot_noStories_returnsEmptyList() {
            when(storyRepository.findByHotspot_HotspotIdAndStatus(anyLong(), any()))
                    .thenReturn(List.of());

            assertTrue(storyService.getByHotspot(2L, null).isEmpty());
        }
    }
}
