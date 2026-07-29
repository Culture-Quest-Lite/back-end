package org.sep490.backend.module.content.service.impl;

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
import org.sep490.backend.module.content.dto.request.TagRequest;
import org.sep490.backend.module.content.dto.response.TagResponse;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.content.entity.enumeration.TagStatus;
import org.sep490.backend.module.content.mapper.TagMapper;
import org.sep490.backend.module.content.repository.RouteRepository;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.content.repository.TagRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test cho luồng QUẢN LÝ TAG (Content).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TagServiceImplTest {

    @Mock private TagRepository tagRepository;
    @Mock private RouteRepository routeRepository;
    @Mock private StoryRepository storyRepository;
    @Mock private TagMapper tagMapper;

    @InjectMocks private TagServiceImpl tagService;

    // =====================================================================
    // Function: create (Tag)
    // =====================================================================
    @Nested
    @DisplayName("createTag")
    class CreateTagTest {

        private TagRequest tagRequest(String name) {
            TagRequest request = new TagRequest();
            request.setTagName(name);
            return request;
        }

        // UTCID01 - Abnormal: tên tag đã tồn tại
        @Test
        void createTag_duplicateName_throwsAlreadyExists() {
            when(tagRepository.existsByTagNameIgnoreCase("Lịch sử")).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> tagService.create(tagRequest("Lịch sử")));

            assertEquals("Tag với tên \"Lịch sử\" đã tồn tại", ex.getMessage());
            verify(tagRepository, never()).save(any());
        }

        // UTCID02 - Normal: tạo tag thành công, trạng thái ACTIVE
        @Test
        void createTag_valid_createsActiveTag() {
            when(tagRepository.existsByTagNameIgnoreCase("Lịch sử")).thenReturn(false);
            Tag tag = new Tag();
            when(tagMapper.toEntity(any(TagRequest.class))).thenReturn(tag);
            when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));
            when(tagMapper.toResponse(any(Tag.class))).thenReturn(new TagResponse());

            TagResponse response = tagService.create(tagRequest("Lịch sử"));

            assertNotNull(response);
            assertEquals(TagStatus.ACTIVE, tag.getTagStatus());
            verify(tagRepository).save(tag);
        }
    }
}
