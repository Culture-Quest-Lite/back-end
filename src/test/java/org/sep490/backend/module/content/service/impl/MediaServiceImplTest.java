package org.sep490.backend.module.content.service.impl;

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
import org.sep490.backend.common.service.TransactionCompensationService;
import org.sep490.backend.module.admin.entity.PartnerInfo;
import org.sep490.backend.module.admin.repository.PartnerInfoRepository;
import org.sep490.backend.module.content.dto.response.MediaResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Media;
import org.sep490.backend.module.content.entity.Review;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.entity.enumeration.MediaTargetType;
import org.sep490.backend.module.content.entity.enumeration.MediaType;
import org.sep490.backend.module.content.mapper.MediaMapper;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.MediaRepository;
import org.sep490.backend.module.content.repository.ReviewRepository;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.content.service.inter.S3Service;
import org.sep490.backend.module.social.entity.Post;
import org.sep490.backend.module.social.repository.PostRepository;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho UPLOAD MEDIA (ảnh / audio / video) gắn vào 5 loại thực thể.
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MediaServiceImplTest {

    @Mock private MediaRepository mediaRepository;
    @Mock private StoryRepository storyRepository;
    @Mock private HotspotRepository hotspotRepository;
    @Mock private PostRepository postRepository;
    @Mock private PartnerInfoRepository partnerInfoRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private S3Service s3Service;
    @Mock private MediaMapper mediaMapper;
    @Mock private TransactionCompensationService txCompensation;

    @InjectMocks private MediaServiceImpl mediaService;

    private static final int KB = 1024;
    private static final int MB = 1024 * 1024;

    /** File ảnh 500KB — dưới hạn mức 1MB. */
    private static MockMultipartFile imageFile() {
        return new MockMultipartFile("files", "anh-da-lat.png", "image/png", new byte[500 * KB]);
    }

    private static MockMultipartFile file(String name, String mimeType, int sizeBytes) {
        return new MockMultipartFile("files", name, mimeType, new byte[sizeBytes]);
    }

    /** Cho save() trả lại chính entity để assert trên đối tượng đã giữ tham chiếu. */
    private void stubSaveAndUpload() {
        when(mediaRepository.save(any(Media.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mediaMapper.toResponse(any(Media.class))).thenReturn(new MediaResponse());
    }

    // =====================================================================
    // Function: uploadAndSaveMedias - xác định thư mục & gắn quan hệ thực thể
    // =====================================================================
    @Nested
    @DisplayName("uploadAndSaveMedias")
    class DetermineFolderAndSetEntityRelationTest {

        // UTCID01 - Boundary: mảng file rỗng -> trả danh sách rỗng, không gọi S3
        @Test
        void uploadAndSaveMedias_emptyFileArray_returnsEmptyList() throws IOException {
            assertTrue(mediaService.uploadAndSaveMedias(
                    new MultipartFile[0], MediaTargetType.POST, 1L).isEmpty());

            verifyNoInteractions(s3Service);
            verify(mediaRepository, never()).save(any());
        }

        // UTCID02 - Boundary: mảng file = null -> trả danh sách rỗng
        @Test
        void uploadAndSaveMedias_nullFileArray_returnsEmptyList() throws IOException {
            assertTrue(mediaService.uploadAndSaveMedias(
                    null, MediaTargetType.POST, 1L).isEmpty());

            verifyNoInteractions(s3Service);
        }

        // UTCID03 - Abnormal: entityType = null
        @Test
        void uploadAndSaveMedias_nullEntityType_throwsEntityTypeRequired() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> mediaService.uploadAndSaveMedias(
                            new MultipartFile[]{imageFile()}, null, 1L));

            assertEquals("Entity type không được null", ex.getMessage());
            verifyNoInteractions(s3Service);
        }

        // UTCID04 - Abnormal: entityId = null
        @Test
        void uploadAndSaveMedias_nullEntityId_throwsEntityIdRequired() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> mediaService.uploadAndSaveMedias(
                            new MultipartFile[]{imageFile()}, MediaTargetType.POST, null));

            assertEquals("Entity ID không được null", ex.getMessage());
            verifyNoInteractions(s3Service);
        }

        // UTCID05 - Normal: gắn vào STORY -> thư mục "stories"
        @Test
        void uploadAndSaveMedias_storyTarget_usesStoriesFolder() throws IOException {
            Story story = new Story();
            story.setStoryId(10L);
            when(storyRepository.findById(10L)).thenReturn(Optional.of(story));
            when(mediaRepository.findMaxDisplayOrderByStoryId(10L)).thenReturn(0);
            stubSaveAndUpload();

            mediaService.uploadAndSaveMedias(
                    new MultipartFile[]{imageFile()}, MediaTargetType.STORY, 10L);

            verify(s3Service).uploadFile(any(MultipartFile.class), eq("stories"));
            ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository).save(captor.capture());
            assertSame(story, captor.getValue().getStory());
        }

        // UTCID06 - Normal: gắn vào HOTSPOT -> thư mục "hotspots"
        @Test
        void uploadAndSaveMedias_hotspotTarget_usesHotspotsFolder() throws IOException {
            Hotspot hotspot = new Hotspot();
            hotspot.setHotspotId(20L);
            when(hotspotRepository.findById(20L)).thenReturn(Optional.of(hotspot));
            when(mediaRepository.findMaxDisplayOrderByHotspotId(20L)).thenReturn(0);
            stubSaveAndUpload();

            mediaService.uploadAndSaveMedias(
                    new MultipartFile[]{imageFile()}, MediaTargetType.HOTSPOT, 20L);

            verify(s3Service).uploadFile(any(MultipartFile.class), eq("hotspots"));
            ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository).save(captor.capture());
            assertSame(hotspot, captor.getValue().getHotspot());
        }

        // UTCID07 - Normal: gắn vào POST -> thư mục "posts"
        @Test
        void uploadAndSaveMedias_postTarget_usesPostsFolder() throws IOException {
            Post post = Post.builder().postId(30L).build();
            when(postRepository.findById(30L)).thenReturn(Optional.of(post));
            when(mediaRepository.findMaxDisplayOrderByPostId(30L)).thenReturn(0);
            stubSaveAndUpload();

            mediaService.uploadAndSaveMedias(
                    new MultipartFile[]{imageFile()}, MediaTargetType.POST, 30L);

            verify(s3Service).uploadFile(any(MultipartFile.class), eq("posts"));
            ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository).save(captor.capture());
            assertSame(post, captor.getValue().getPost());
        }

        // UTCID08 - Normal: gắn vào PARTNER_SUBSCRIPTION -> thư mục "partner_subscriptions"
        @Test
        void uploadAndSaveMedias_partnerTarget_usesPartnerFolder() throws IOException {
            PartnerInfo partnerInfo = PartnerInfo.builder().partnerInfoId(40L).build();
            when(partnerInfoRepository.findById(40L)).thenReturn(Optional.of(partnerInfo));
            when(mediaRepository.findMaxDisplayOrderByPartnerInfoId(40L)).thenReturn(0);
            stubSaveAndUpload();

            mediaService.uploadAndSaveMedias(
                    new MultipartFile[]{imageFile()}, MediaTargetType.PARTNER_SUBSCRIPTION, 40L);

            verify(s3Service).uploadFile(any(MultipartFile.class), eq("partner_subscriptions"));
            ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository).save(captor.capture());
            assertSame(partnerInfo, captor.getValue().getPartnerInfo());
        }

        // UTCID09 - Normal: gắn vào REVIEW -> thư mục "reviews"
        @Test
        void uploadAndSaveMedias_reviewTarget_usesReviewsFolder() throws IOException {
            Review review = Review.builder().reviewId(50L).build();
            when(reviewRepository.findById(50L)).thenReturn(Optional.of(review));
            when(mediaRepository.findMaxDisplayOrderByReviewId(50L)).thenReturn(0);
            stubSaveAndUpload();

            mediaService.uploadAndSaveMedias(
                    new MultipartFile[]{imageFile()}, MediaTargetType.REVIEW, 50L);

            verify(s3Service).uploadFile(any(MultipartFile.class), eq("reviews"));
            ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository).save(captor.capture());
            assertSame(review, captor.getValue().getReview());
        }

        // UTCID10 - Abnormal: bài viết đích không tồn tại
        @Test
        void uploadAndSaveMedias_postNotFound_throwsPostNotFound() {
            when(postRepository.findById(30L)).thenReturn(Optional.empty());
            when(mediaRepository.findMaxDisplayOrderByPostId(30L)).thenReturn(0);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> mediaService.uploadAndSaveMedias(
                            new MultipartFile[]{imageFile()}, MediaTargetType.POST, 30L));

            assertEquals("Post không tồn tại với ID: 30", ex.getMessage());
            verifyNoInteractions(s3Service);
        }

        // UTCID11 - Abnormal: đánh giá đích không tồn tại
        @Test
        void uploadAndSaveMedias_reviewNotFound_throwsReviewNotFound() {
            when(reviewRepository.findById(50L)).thenReturn(Optional.empty());
            when(mediaRepository.findMaxDisplayOrderByReviewId(50L)).thenReturn(0);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> mediaService.uploadAndSaveMedias(
                            new MultipartFile[]{imageFile()}, MediaTargetType.REVIEW, 50L));

            assertEquals("Đánh giá không tồn tại với ID: 50", ex.getMessage());
        }

        // UTCID12 - Abnormal: file rỗng
        @Test
        void uploadAndSaveMedias_emptyFile_throwsFileRequired() {
            when(mediaRepository.findMaxDisplayOrderByPostId(30L)).thenReturn(0);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> mediaService.uploadAndSaveMedias(
                            new MultipartFile[]{file("rong.png", "image/png", 0)},
                            MediaTargetType.POST, 30L));

            assertEquals("File không được trống", ex.getMessage());
        }

        // UTCID13 - Normal: upload nhiều file -> displayOrder tăng dần từ maxOrder + 1
        @Test
        void uploadAndSaveMedias_multipleFiles_incrementsDisplayOrderFromMax() throws IOException {
            Post post = Post.builder().postId(30L).build();
            when(postRepository.findById(30L)).thenReturn(Optional.of(post));
            when(mediaRepository.findMaxDisplayOrderByPostId(30L)).thenReturn(5);
            stubSaveAndUpload();

            mediaService.uploadAndSaveMedias(
                    new MultipartFile[]{imageFile(), imageFile(), imageFile()},
                    MediaTargetType.POST, 30L);

            ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository, times(3)).save(captor.capture());
            assertEquals(6, captor.getAllValues().get(0).getDisplayOrder());
            assertEquals(7, captor.getAllValues().get(1).getDisplayOrder());
            assertEquals(8, captor.getAllValues().get(2).getDisplayOrder());
        }
    }

    // =====================================================================
    // Function: validateFileSize (qua uploadAndSaveMedias)
    // =====================================================================
    @Nested
    @DisplayName("validateFileSize")
    class ValidateFileSizeTest {

        private void stubPostTarget() {
            when(postRepository.findById(30L))
                    .thenReturn(Optional.of(Post.builder().postId(30L).build()));
            when(mediaRepository.findMaxDisplayOrderByPostId(30L)).thenReturn(0);
        }

        // UTCID01 - Abnormal: ảnh 2MB vượt hạn mức 1MB
        @Test
        void validateFileSize_imageOverOneMb_throwsSizeExceeded() {
            stubPostTarget();

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> mediaService.uploadAndSaveMedias(
                            new MultipartFile[]{file("anh-lon.png", "image/png", 2 * MB)},
                            MediaTargetType.POST, 30L));

            assertEquals("Ảnh 'anh-lon.png' vượt quá dung lượng cho phép (2.0MB). Tối đa: 1MB",
                    ex.getMessage());
            verifyNoInteractions(s3Service);
        }

        // UTCID02 - Boundary: ảnh đúng 1MB (bằng hạn mức) -> vẫn cho qua
        @Test
        void validateFileSize_imageExactlyOneMb_isAccepted() throws IOException {
            stubPostTarget();
            stubSaveAndUpload();

            assertDoesNotThrow(() -> mediaService.uploadAndSaveMedias(
                    new MultipartFile[]{file("anh-1mb.png", "image/png", MB)},
                    MediaTargetType.POST, 30L));

            verify(mediaRepository).save(any(Media.class));
        }

        // UTCID03 - Abnormal: audio 25MB vượt hạn mức 20MB
        @Test
        void validateFileSize_audioOverTwentyMb_throwsSizeExceeded() {
            stubPostTarget();

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> mediaService.uploadAndSaveMedias(
                            new MultipartFile[]{file("thuyet-minh.mp3", "audio/mpeg", 25 * MB)},
                            MediaTargetType.POST, 30L));

            assertEquals("Audio 'thuyet-minh.mp3' vượt quá dung lượng cho phép (25.0MB). "
                    + "Tối đa: 20MB", ex.getMessage());
        }

        // UTCID04 - Boundary: audio đúng 20MB -> được chấp nhận
        @Test
        void validateFileSize_audioExactlyTwentyMb_isAccepted() throws IOException {
            stubPostTarget();
            stubSaveAndUpload();

            assertDoesNotThrow(() -> mediaService.uploadAndSaveMedias(
                    new MultipartFile[]{file("thuyet-minh.mp3", "audio/mpeg", 20 * MB)},
                    MediaTargetType.POST, 30L));
        }

        // UTCID05 - Normal: video 10MB dưới hạn mức 200MB -> hợp lệ, loại VIDEO
        @Test
        void validateFileSize_videoUnderLimit_isAcceptedAsVideoType() throws IOException {
            stubPostTarget();
            stubSaveAndUpload();

            mediaService.uploadAndSaveMedias(
                    new MultipartFile[]{file("clip.mp4", "video/mp4", 10 * MB)},
                    MediaTargetType.POST, 30L);

            ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository).save(captor.capture());
            assertEquals(MediaType.VIDEO, captor.getValue().getMediaType());
        }

        // UTCID06 - Boundary: file loại OTHER (pdf) -> không bị giới hạn dung lượng
        @Test
        void validateFileSize_otherTypeIsNotSizeLimited() throws IOException {
            stubPostTarget();
            stubSaveAndUpload();

            mediaService.uploadAndSaveMedias(
                    new MultipartFile[]{file("giay-phep.pdf", "application/pdf", 300 * MB)},
                    MediaTargetType.POST, 30L);

            ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository).save(captor.capture());
            assertEquals(MediaType.OTHER, captor.getValue().getMediaType());
        }

        // UTCID07 - Boundary: mimeType = null -> xếp loại OTHER, không ném lỗi
        @Test
        void validateFileSize_nullMimeType_classifiedAsOther() throws IOException {
            stubPostTarget();
            stubSaveAndUpload();

            mediaService.uploadAndSaveMedias(
                    new MultipartFile[]{file("khong-ro.bin", null, 5 * MB)},
                    MediaTargetType.POST, 30L);

            ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository).save(captor.capture());
            assertEquals(MediaType.OTHER, captor.getValue().getMediaType());
            assertNull(captor.getValue().getMimeType());
        }

        // UTCID08 - Boundary: tên file quá 50 ký tự -> bị cắt ngắn nhưng giữ đuôi mở rộng
        @Test
        void uploadAndSaveMedias_longFileName_isTruncatedKeepingExtension() throws IOException {
            stubPostTarget();
            stubSaveAndUpload();

            String longName = "anh_du_lich_da_lat_thang_tam_nam_hai_nghin_khong_tram_hai_muoi_sau.png";
            mediaService.uploadAndSaveMedias(
                    new MultipartFile[]{file(longName, "image/png", 500 * KB)},
                    MediaTargetType.POST, 30L);

            ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository).save(captor.capture());
            String saved = captor.getValue().getFileName();
            assertEquals(50, saved.length());
            assertTrue(saved.endsWith(".png"));
        }

        // UTCID09 - Boundary: tên file có khoảng trắng -> thay bằng dấu gạch dưới
        @Test
        void uploadAndSaveMedias_fileNameWithSpaces_replacedWithUnderscore() throws IOException {
            stubPostTarget();
            stubSaveAndUpload();

            mediaService.uploadAndSaveMedias(
                    new MultipartFile[]{file("anh da lat.png", "image/png", 500 * KB)},
                    MediaTargetType.POST, 30L);

            ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository).save(captor.capture());
            assertEquals("anh_da_lat.png", captor.getValue().getFileName());
        }
    }

    // =====================================================================
    // Function: deleteMedia
    // =====================================================================
    @Nested
    @DisplayName("deleteMedia")
    class DeleteMediaTest {

        // UTCID01 - Abnormal: media không tồn tại
        @Test
        void deleteMedia_notFound_throwsMediaNotFound() {
            when(mediaRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> mediaService.deleteMedia(1L));

            assertEquals("Media không tồn tại với ID: 1", ex.getMessage());
            verify(mediaRepository, never()).delete(any());
        }

        // UTCID02 - Normal: xóa bản ghi và lên lịch xóa file S3 sau khi commit
        @Test
        void deleteMedia_withFileUrl_deletesRowAndSchedulesS3Deletion() {
            Media target = new Media();
            target.setMediaId(1L);
            target.setFileUrl("https://s3/posts/anh.png");
            when(mediaRepository.findById(1L)).thenReturn(Optional.of(target));

            mediaService.deleteMedia(1L);

            verify(mediaRepository).delete(target);
            verify(txCompensation).runAfterCommit(anyString(), any(Runnable.class));
        }

        // UTCID03 - Boundary: media không có fileUrl -> chỉ xóa bản ghi, không đụng S3
        @Test
        void deleteMedia_nullFileUrl_deletesRowOnly() {
            Media target = new Media();
            target.setMediaId(1L);
            target.setFileUrl(null);
            when(mediaRepository.findById(1L)).thenReturn(Optional.of(target));

            mediaService.deleteMedia(1L);

            verify(mediaRepository).delete(target);
            verify(txCompensation, never()).runAfterCommit(anyString(), any(Runnable.class));
        }
    }
}
