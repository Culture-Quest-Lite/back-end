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
import org.sep490.backend.module.content.mapper.StoryMapper;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.content.repository.TagRepository;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.content.service.inter.MediaService;
import org.sep490.backend.module.user.service.UserService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
            when(tagRepository.findById(1L)).thenReturn(Optional.of(new Tag()));
            when(hotspotService.getById(2L)).thenThrow(new BusinessException("Không tìm thấy Hotspot"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> storyService.create(request));

            assertEquals("Không tìm thấy Hotspot", ex.getMessage());
        }

        // UTCID03 - Normal: tạo story thành công, trạng thái DRAFT
        @Test
        void createStory_valid_createsDraftStory() {
            StoryRequest request = storyRequest();
            when(tagRepository.findById(1L)).thenReturn(Optional.of(new Tag()));
            when(hotspotService.getById(2L)).thenReturn(new Hotspot());
            Story story = new Story();
            when(storyMapper.toEntity(request)).thenReturn(story);
            when(userService.getCurrentUser()).thenReturn(new User());
            when(storyRepository.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));
            when(storyMapper.toResponse(any(Story.class))).thenReturn(new StoryResponse());

            StoryResponse response = storyService.create(request);

            assertNotNull(response);
            assertEquals(ContentStatus.DRAFT, story.getStatus());
            verify(storyRepository).save(story);
        }
    }
}
