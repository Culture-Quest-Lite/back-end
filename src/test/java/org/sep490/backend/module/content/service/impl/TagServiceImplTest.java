package org.sep490.backend.module.content.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import org.sep490.backend.module.content.dto.record.CultureCheckResult;
import org.sep490.backend.module.content.entity.enumeration.CultureDecision;
import org.sep490.backend.module.content.service.inter.CultureGuardService;
import org.sep490.backend.module.content.service.inter.ImageService;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Unit test cho luồng QUẢN LÝ TAG (Content).
 * Phần validate/upload ảnh nằm ở EntityImageServiceImplTest — ở đây chỉ kiểm tra việc ủy quyền.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TagServiceImplTest {

    @Mock private TagRepository tagRepository;
    @Mock private RouteRepository routeRepository;
    @Mock private StoryRepository storyRepository;
    @Mock private TagMapper tagMapper;
    @Mock private ImageService imageService;
    @Mock private CultureGuardService cultureGuardService;

    @InjectMocks private TagServiceImpl tagService;

    @BeforeEach
    void guardPassesByDefault() {
        when(cultureGuardService.checkAndEnforce(anyString(), anyString(), any()))
                .thenReturn(culture(CultureDecision.PASS));
    }

    private static CultureCheckResult culture(CultureDecision decision) {
        return CultureCheckResult.rule(decision, decision == CultureDecision.PASS ? 1d : 0.4d, "lý do", java.util.List.of());
    }

    private static MockMultipartFile imageFile() {
        return new MockMultipartFile("imageFile", "icon.png", "image/png", new byte[1024]);
    }

    private static TagRequest tagRequest(String name) {
        TagRequest request = new TagRequest();
        request.setTagName(name);
        return request;
    }

    // =====================================================================
    // Function: create (Tag)
    // =====================================================================
    @Nested
    @DisplayName("createTag")
    class CreateTagTest {

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
            tag.setTagId(1L);
            tag.setTagName("Lịch sử");
            when(tagMapper.toEntity(any(TagRequest.class))).thenReturn(tag);
            when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));
            when(tagMapper.toResponse(any(Tag.class))).thenReturn(new TagResponse());

            TagResponse response = tagService.create(tagRequest("Lịch sử"));

            assertNotNull(response);
            assertEquals(TagStatus.ACTIVE, tag.getTagStatus());
            verify(tagRepository).save(tag);
        }

        // UTCID03 - Normal: tạo tag kèm ảnh, imageUrl lấy từ helper
        @Test
        void createTag_withImage_setsImageUrlFromHelper() {
            TagRequest request = tagRequest("Ẩm thực");
            request.setImageFile(imageFile());

            when(tagRepository.existsByTagNameIgnoreCase("Ẩm thực")).thenReturn(false);
            Tag tag = new Tag();
            tag.setTagId(1L);
            tag.setTagName("Lịch sử");
            when(tagMapper.toEntity(any(TagRequest.class))).thenReturn(tag);
            when(imageService.resolveImageUrl(isNull(), any(MultipartFile.class), eq("tags")))
                    .thenReturn("https://s3/tags/icon.png");
            when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));
            when(tagMapper.toResponse(any(Tag.class))).thenReturn(new TagResponse());

            tagService.create(request);

            assertEquals("https://s3/tags/icon.png", tag.getImageUrl());
        }

        // UTCID04 - Abnormal: bộ lọc văn hóa từ chối thì không được lưu
        @Test
        void createTag_cultureRejected_doesNotSave() {
            when(tagRepository.existsByTagNameIgnoreCase("Crypto trading")).thenReturn(false);
            when(cultureGuardService.checkAndEnforce(anyString(), anyString(), any()))
                    .thenThrow(new BusinessException("Nội dung không phù hợp"));

            assertThrows(BusinessException.class, () -> tagService.create(tagRequest("Crypto trading")));

            verify(tagRepository, never()).save(any());
        }

        // UTCID05 - Normal: vùng xám đã xác nhận thì lưu ở trạng thái chờ duyệt
        @Test
        void createTag_cultureReview_savesAsPendingReview() {
            when(tagRepository.existsByTagNameIgnoreCase("Cà phê sáng")).thenReturn(false);
            when(cultureGuardService.checkAndEnforce(anyString(), anyString(), any()))
                    .thenReturn(culture(CultureDecision.REVIEW));
            Tag tag = new Tag();
            when(tagMapper.toEntity(any(TagRequest.class))).thenReturn(tag);
            when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));
            when(tagMapper.toResponse(any(Tag.class))).thenReturn(new TagResponse());

            TagRequest request = tagRequest("Cà phê sáng");
            request.setConfirmCultural(true);
            tagService.create(request);

            assertEquals(TagStatus.PENDING_REVIEW, tag.getTagStatus());
            assertEquals(0.4d, tag.getCultureScore());
            assertNotNull(tag.getCultureCheckedAt());
        }
    }

    // =====================================================================
    // Function: update (Tag) - sau khi bi tu choi
    // =====================================================================
    @Nested
    @DisplayName("updateTag sau khi bi tu choi")
    class UpdateRejectedTagTest {

        // UTCID01 - Normal: tag bi tu choi sua lai, du bo loc PASS van phai cho admin duyet lai
        @Test
        void updateTag_rejectedThenPasses_goesBackToPendingReview() {
            Tag tag = new Tag();
            tag.setTagId(1L);
            tag.setTagStatus(TagStatus.REJECTED);
            tag.setRejectReason("Không liên quan văn hóa");
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
            when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));
            when(tagMapper.toResponse(any(Tag.class))).thenReturn(new TagResponse());

            tagService.update(1L, tagRequest("Lễ hội Gióng"));

            assertEquals(TagStatus.PENDING_REVIEW, tag.getTagStatus());
            assertNull(tag.getRejectReason());
        }

        // UTCID02 - Normal: tag dang ACTIVE ma bo loc PASS thi giu nguyen trang thai
        @Test
        void updateTag_activeAndPasses_keepsActive() {
            Tag tag = new Tag();
            tag.setTagId(1L);
            tag.setTagStatus(TagStatus.ACTIVE);
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
            when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));
            when(tagMapper.toResponse(any(Tag.class))).thenReturn(new TagResponse());

            tagService.update(1L, tagRequest("Lễ hội Gióng"));

            assertEquals(TagStatus.ACTIVE, tag.getTagStatus());
        }
    }

    // =====================================================================
    // Function: update (Tag)
    // =====================================================================
    @Nested
    @DisplayName("updateTag")
    class UpdateTagTest {

        private Tag existingTag(String imageUrl) {
            Tag tag = new Tag();
            tag.setTagId(1L);
            tag.setTagName("Lịch sử");
            tag.setTagStatus(TagStatus.ACTIVE);
            tag.setImageUrl(imageUrl);
            return tag;
        }

        // UTCID01 - Abnormal: tag đang bị vô hiệu hóa
        @Test
        void updateTag_inactiveTag_throws() {
            Tag tag = existingTag(null);
            tag.setTagStatus(TagStatus.INACTIVE);
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> tagService.update(1L, tagRequest("Lịch sử")));

            assertEquals("Tag đang bị vô hiệu hóa, không thể cập nhật", ex.getMessage());
            verify(imageService, never()).resolveImageUrl(any(), any(), any());
        }

        // UTCID02 - Normal: truyền ảnh hiện tại + request xuống helper, gán kết quả trả về
        @Test
        void updateTag_delegatesImageResolutionWithCurrentUrl() {
            Tag tag = existingTag("https://s3/tags/old.png");
            TagRequest request = tagRequest("Lịch sử");
            request.setImageFile(imageFile());

            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
            when(imageService.resolveImageUrl(any(), any(), any()))
                    .thenReturn("https://s3/tags/new.png");
            when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));
            when(tagMapper.toResponse(any(Tag.class))).thenReturn(new TagResponse());

            tagService.update(1L, request);

            ArgumentCaptor<String> currentUrl = ArgumentCaptor.forClass(String.class);
            verify(imageService).resolveImageUrl(
                    currentUrl.capture(), any(MultipartFile.class), eq("tags"));
            assertEquals("https://s3/tags/old.png", currentUrl.getValue());
            assertEquals("https://s3/tags/new.png", tag.getImageUrl());
        }

        // UTCID03 - Normal: không gửi ảnh mới thì giữ nguyên ảnh cũ
        @Test
        void updateTag_noNewImage_keepsCurrentImage() {
            Tag tag = existingTag("https://s3/tags/old.png");

            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
            when(imageService.resolveImageUrl(any(), any(), any()))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));
            when(tagMapper.toResponse(any(Tag.class))).thenReturn(new TagResponse());

            tagService.update(1L, tagRequest("Lịch sử"));

            verify(imageService).resolveImageUrl(
                    eq("https://s3/tags/old.png"), isNull(), eq("tags"));
            assertEquals("https://s3/tags/old.png", tag.getImageUrl());
        }

        // UTCID04 - Abnormal: tên mới trùng với tag khác -> chặn
        @Test
        void updateTag_duplicateNameOnOtherTag_throwsAlreadyExists() {
            when(tagRepository.findById(1L)).thenReturn(Optional.of(existingTag(null)));
            when(tagRepository.existsByTagNameIgnoreCaseAndTagIdNot("Ẩm thực", 1L)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> tagService.update(1L, tagRequest("Ẩm thực")));

            assertEquals("Tag với tên \"Ẩm thực\" đã tồn tại", ex.getMessage());
            verify(tagRepository, never()).save(any());
        }

        // UTCID05 - Boundary: tên mới có khoảng trắng thừa -> được cắt bỏ trước khi lưu
        @Test
        void updateTag_nameWithExtraSpaces_isTrimmed() {
            Tag tag = existingTag(null);
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
            when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));
            when(tagMapper.toResponse(any(Tag.class))).thenReturn(new TagResponse());

            tagService.update(1L, tagRequest("  Ẩm thực  "));

            assertEquals("Ẩm thực", tag.getTagName());
        }
    }

    // =====================================================================
    // Function: delete (Tag)
    // =====================================================================
    @Nested
    @DisplayName("deleteTag")
    class DeleteTagTest {

        // UTCID01 - Abnormal: tag không tồn tại
        @Test
        void deleteTag_notFound_throwsNotFound() {
            when(tagRepository.findById(99L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> tagService.delete(99L));

            assertEquals("Không tìm thấy tag với id: 99", ex.getMessage());
            verify(tagRepository, never()).save(any());
        }

        // UTCID02 - Abnormal: tag đã bị xóa trước đó -> chặn xóa lần hai
        @Test
        void deleteTag_alreadyDeleted_throwsAlreadyDeleted() {
            Tag tag = new Tag();
            tag.setTagId(1L);
            tag.setTagName("Lịch sử");
            tag.setTagStatus(TagStatus.DELETED);
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> tagService.delete(1L));

            assertEquals("Tag với id 1 đã bị xóa", ex.getMessage());
            verify(tagRepository, never()).save(any());
        }

        // UTCID03 - Normal: tag ACTIVE -> xóa mềm, chuyển trạng thái DELETED
        @Test
        void deleteTag_activeTag_softDeletes() {
            Tag tag = new Tag();
            tag.setTagId(1L);
            tag.setTagName("Lịch sử");
            tag.setTagStatus(TagStatus.ACTIVE);
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));

            tagService.delete(1L);

            assertEquals(TagStatus.DELETED, tag.getTagStatus());
            verify(tagRepository).save(tag);
        }

        // UTCID04 - Normal: tag INACTIVE vẫn xóa được (khác với update)
        @Test
        void deleteTag_inactiveTag_canStillBeDeleted() {
            Tag tag = new Tag();
            tag.setTagId(1L);
            tag.setTagName("Lịch sử");
            tag.setTagStatus(TagStatus.INACTIVE);
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));

            tagService.delete(1L);

            assertEquals(TagStatus.DELETED, tag.getTagStatus());
        }
    }

    // =====================================================================
    // Function: getById (Tag)
    // =====================================================================
    @Nested
    @DisplayName("getById")
    class GetByIdTest {

        // UTCID01 - Abnormal: id không tồn tại
        @Test
        void getById_notFound_throwsNotFound() {
            when(tagRepository.findById(99L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class, () -> tagService.getById(99L));

            assertEquals("Không tìm thấy tag với id: 99", ex.getMessage());
        }

        // UTCID02 - Abnormal: tag đã bị xóa mềm -> coi như không dùng được
        @Test
        void getById_deletedTag_throwsDeleted() {
            Tag tag = new Tag();
            tag.setTagId(1L);
            tag.setTagName("Lịch sử");
            tag.setTagStatus(TagStatus.DELETED);
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));

            BusinessException ex = assertThrows(BusinessException.class, () -> tagService.getById(1L));

            assertEquals("Tag với id 1 đã bị xóa", ex.getMessage());
        }

        // UTCID03 - Normal: tag ACTIVE -> trả về entity
        @Test
        void getById_activeTag_returnsEntity() {
            Tag tag = new Tag();
            tag.setTagId(1L);
            tag.setTagName("Lịch sử");
            tag.setTagStatus(TagStatus.ACTIVE);
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));

            assertSame(tag, tagService.getById(1L));
        }

        // UTCID04 - Normal: tag INACTIVE vẫn đọc được (chỉ chặn khi DELETED)
        @Test
        void getById_inactiveTag_returnsEntity() {
            Tag tag = new Tag();
            tag.setTagId(1L);
            tag.setTagName("Lịch sử");
            tag.setTagStatus(TagStatus.INACTIVE);
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));

            assertSame(tag, tagService.getById(1L));
        }
    }
}
