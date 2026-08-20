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
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.dto.request.CultureRejectRequest;
import org.sep490.backend.module.content.dto.response.CultureContentResponse;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.dto.response.TagResponse;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.sep490.backend.module.content.entity.enumeration.TagStatus;
import org.sep490.backend.module.content.mapper.StoryMapper;
import org.sep490.backend.module.content.mapper.TagMapper;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.content.repository.TagRepository;
import org.sep490.backend.module.user.service.UserService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test cho hàng chờ kiểm duyệt văn hóa.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CultureModerationServiceImplTest {

    @Mock private TagRepository tagRepository;
    @Mock private StoryRepository storyRepository;
    @Mock private TagMapper tagMapper;
    @Mock private StoryMapper storyMapper;
    @Mock private UserService userService;

    @InjectMocks private CultureModerationServiceImpl service;

    private static Tag tagWith(TagStatus status) {
        Tag tag = new Tag();
        tag.setTagId(1L);
        tag.setTagName("Chợ đêm");
        tag.setTagStatus(status);
        return tag;
    }

    private static Story storyWith(ContentStatus status) {
        Story story = new Story();
        story.setStoryId(10L);
        story.setTitle("Chuyện xưa");
        story.setStatus(status);
        return story;
    }

    private void currentUserIs(Long id) {
        User user = new User();
        user.setUserId(id);
        when(userService.getCurrentUser()).thenReturn(user);
    }

    // =====================================================================
    // Function: getPending / getRejected
    // =====================================================================
    @Nested
    @DisplayName("hang cho")
    class QueueTest {

        // UTCID01 - Normal: hàng chờ duyệt chỉ lấy PENDING_REVIEW
        @Test
        void getPending_queriesPendingReviewOnly() {
            when(tagRepository.findByTagStatusOrderByCreatedAtAsc(TagStatus.PENDING_REVIEW))
                    .thenReturn(List.of(tagWith(TagStatus.PENDING_REVIEW)));
            when(storyRepository.findByStatus(ContentStatus.PENDING_REVIEW))
                    .thenReturn(List.of(storyWith(ContentStatus.PENDING_REVIEW)));
            when(tagMapper.toResponse(any(Tag.class))).thenReturn(new TagResponse());
            when(storyMapper.toResponse(any(Story.class))).thenReturn(new StoryResponse());

            CultureContentResponse response = service.getPending();

            assertEquals(1, response.getTags().size());
            assertEquals(1, response.getStories().size());
            verify(tagRepository).findByTagStatusOrderByCreatedAtAsc(TagStatus.PENDING_REVIEW);
            verify(storyRepository).findByStatus(ContentStatus.PENDING_REVIEW);
        }

        // UTCID02 - Normal: danh sách bị từ chối chỉ lấy REJECTED, đây là chỗ curator vào sửa
        @Test
        void getRejected_queriesRejectedOnly() {
            when(tagRepository.findByTagStatusOrderByCreatedAtAsc(TagStatus.REJECTED))
                    .thenReturn(List.of(tagWith(TagStatus.REJECTED)));
            when(storyRepository.findByStatus(ContentStatus.REJECTED))
                    .thenReturn(List.of(storyWith(ContentStatus.REJECTED)));
            when(tagMapper.toResponse(any(Tag.class))).thenReturn(new TagResponse());
            when(storyMapper.toResponse(any(Story.class))).thenReturn(new StoryResponse());

            CultureContentResponse response = service.getRejected();

            assertEquals(1, response.getTags().size());
            assertEquals(1, response.getStories().size());
            verify(tagRepository).findByTagStatusOrderByCreatedAtAsc(TagStatus.REJECTED);
            verify(storyRepository).findByStatus(ContentStatus.REJECTED);
        }
    }

    // =====================================================================
    // Function: approve / reject
    // =====================================================================
    @Nested
    @DisplayName("duyet va tu choi")
    class ModerateTest {

        // UTCID03 - Normal: duyệt tag chờ -> ACTIVE, ghi lại người duyệt
        @Test
        void approveTag_pendingTag_becomesActive() {
            Tag tag = tagWith(TagStatus.PENDING_REVIEW);
            tag.setRejectReason("lý do cũ");
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
            when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));
            when(tagMapper.toResponse(any(Tag.class))).thenReturn(new TagResponse());
            currentUserIs(99L);

            service.approveTag(1L);

            assertEquals(TagStatus.ACTIVE, tag.getTagStatus());
            assertNull(tag.getRejectReason());
            assertEquals(99L, tag.getModerateBy());
            assertNotNull(tag.getModerateAt());
        }

        // UTCID04 - Normal: từ chối tag -> REJECTED chứ không phải INACTIVE, để còn sửa lại được
        @Test
        void rejectTag_pendingTag_becomesRejected() {
            Tag tag = tagWith(TagStatus.PENDING_REVIEW);
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
            when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));
            when(tagMapper.toResponse(any(Tag.class))).thenReturn(new TagResponse());
            currentUserIs(99L);

            CultureRejectRequest request = new CultureRejectRequest();
            request.setRejectReason("Không liên quan văn hóa");
            service.rejectTag(1L, request);

            assertEquals(TagStatus.REJECTED, tag.getTagStatus());
            assertEquals("Không liên quan văn hóa", tag.getRejectReason());
        }

        // UTCID05 - Abnormal: tag không ở hàng chờ thì không được duyệt
        @Test
        void approveTag_notPending_throws() {
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tagWith(TagStatus.ACTIVE)));

            assertThrows(BusinessException.class, () -> service.approveTag(1L));
            verify(tagRepository, never()).save(any());
        }

        // UTCID06 - Normal: duyệt story chờ -> quay về DRAFT để curator tự publish
        @Test
        void approveStory_pendingStory_becomesDraft() {
            Story story = storyWith(ContentStatus.PENDING_REVIEW);
            when(storyRepository.findById(10L)).thenReturn(Optional.of(story));
            when(storyRepository.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));
            when(storyMapper.toResponse(any(Story.class))).thenReturn(new StoryResponse());
            currentUserIs(99L);

            service.approveStory(10L);

            assertEquals(ContentStatus.DRAFT, story.getStatus());
            assertEquals(99L, story.getModerateBy());
        }

        // UTCID07 - Abnormal: story không ở hàng chờ thì không được từ chối
        @Test
        void rejectStory_notPending_throws() {
            when(storyRepository.findById(10L)).thenReturn(Optional.of(storyWith(ContentStatus.PUBLISHED)));

            CultureRejectRequest request = new CultureRejectRequest();
            request.setRejectReason("lý do");

            assertThrows(BusinessException.class, () -> service.rejectStory(10L, request));
            verify(storyRepository, never()).save(any());
        }
    }
}
