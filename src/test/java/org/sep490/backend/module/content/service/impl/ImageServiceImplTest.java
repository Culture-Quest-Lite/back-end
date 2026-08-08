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
import org.sep490.backend.common.service.TransactionCompensationService;
import org.sep490.backend.module.content.service.inter.S3Service;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit test cho helper xử lý ảnh đơn gắn vào entity (tags/vouchers/routes).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImageServiceImplTest {

    private static final String FOLDER = "tags";

    @Mock private S3Service s3Service;
    @Mock private TransactionCompensationService txCompensation;

    @InjectMocks private ImageServiceImpl entityImageService;

    private static MockMultipartFile file(String name, String contentType, int sizeBytes) {
        return new MockMultipartFile("imageFile", name, contentType, new byte[sizeBytes]);
    }

    // =====================================================================
    // Function: uploadImage
    // =====================================================================
    @Nested
    @DisplayName("uploadImage")
    class UploadImageTest {

        // UTCID01 - Normal: ảnh hợp lệ trả về URL S3
        @Test
        void uploadImage_validImage_returnsS3Url() throws Exception {
            when(s3Service.uploadFile(any(), eq(FOLDER))).thenReturn("https://s3/tags/icon.png");

            String url = entityImageService.uploadImage(file("icon.png", "image/png", 1024), FOLDER);

            assertEquals("https://s3/tags/icon.png", url);
        }

        // UTCID02 - Abnormal: file rỗng
        @Test
        void uploadImage_emptyFile_throws() throws Exception {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> entityImageService.uploadImage(file("icon.png", "image/png", 0), FOLDER));

            assertEquals("File không được trống", ex.getMessage());
            verify(s3Service, never()).uploadFile(any(), anyString());
        }

        // UTCID03 - Abnormal: không phải ảnh
        @Test
        void uploadImage_nonImage_throws() throws Exception {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> entityImageService.uploadImage(file("doc.pdf", "application/pdf", 1024), FOLDER));

            assertEquals("File tải lên phải là ảnh", ex.getMessage());
            verify(s3Service, never()).uploadFile(any(), anyString());
        }

        // UTCID04 - Abnormal: mime type null
        @Test
        void uploadImage_nullMimeType_throws() throws Exception {
            assertThrows(BusinessException.class,
                    () -> entityImageService.uploadImage(file("unknown", null, 1024), FOLDER));

            verify(s3Service, never()).uploadFile(any(), anyString());
        }

        // UTCID05 - Abnormal: ảnh vượt quá 1MB
        @Test
        void uploadImage_oversized_throws() throws Exception {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> entityImageService.uploadImage(file("big.png", "image/png", 2 * 1024 * 1024), FOLDER));

            assertTrue(ex.getMessage().contains("vượt quá dung lượng cho phép"));
            verify(s3Service, never()).uploadFile(any(), anyString());
        }

        // UTCID06 - Abnormal: S3 lỗi thì bọc thành BusinessException
        @Test
        void uploadImage_s3Failure_wrapsAsBusinessException() throws Exception {
            when(s3Service.uploadFile(any(), eq(FOLDER))).thenThrow(new IOException("mạng lỗi"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> entityImageService.uploadImage(file("icon.png", "image/png", 1024), FOLDER));

            assertTrue(ex.getMessage().contains("Lỗi tải ảnh lên S3"));
        }
    }

    // =====================================================================
    // Function: resolveImageUrl
    // =====================================================================
    @Nested
    @DisplayName("resolveImageUrl")
    class ResolveImageUrlTest {

        // UTCID01 - Normal: thêm ảnh lần đầu, không có gì để xóa
        @Test
        void resolveImageUrl_firstImage_noDeletionScheduled() throws Exception {
            when(s3Service.uploadFile(any(), eq(FOLDER))).thenReturn("https://s3/tags/new.png");

            String url = entityImageService.resolveImageUrl(
                    null, file("new.png", "image/png", 1024), FOLDER);

            assertEquals("https://s3/tags/new.png", url);
            verify(txCompensation, never()).runAfterCommit(anyString(), any(Runnable.class));
        }

        // UTCID02 - Normal: thay ảnh, ảnh cũ được lên lịch xóa sau commit
        @Test
        void resolveImageUrl_replaceImage_schedulesOldDeletionAfterCommit() throws Exception {
            when(s3Service.uploadFile(any(), eq(FOLDER))).thenReturn("https://s3/tags/new.png");

            String url = entityImageService.resolveImageUrl(
                    "https://s3/tags/old.png", file("new.png", "image/png", 1024), FOLDER);

            assertEquals("https://s3/tags/new.png", url);
            verify(txCompensation).runAfterCommit(contains("https://s3/tags/old.png"), any(Runnable.class));
            // Chưa xóa ngay — chỉ xóa khi transaction commit thành công
            verify(s3Service, never()).safeDeleteByUrl(anyString());
        }

        // UTCID03 - Normal: không gửi file mới thì giữ nguyên ảnh cũ
        @Test
        void resolveImageUrl_noNewFile_keepsCurrentUrl() throws Exception {
            String url = entityImageService.resolveImageUrl(
                    "https://s3/tags/old.png", null, FOLDER);

            assertEquals("https://s3/tags/old.png", url);
            verify(s3Service, never()).uploadFile(any(), anyString());
            verify(txCompensation, never()).runAfterCommit(anyString(), any(Runnable.class));
        }

        // UTCID04 - Normal: file rỗng cũng coi như không gửi
        @Test
        void resolveImageUrl_emptyFile_keepsCurrentUrl() throws Exception {
            String url = entityImageService.resolveImageUrl(
                    "https://s3/tags/old.png", file("icon.png", "image/png", 0), FOLDER);

            assertEquals("https://s3/tags/old.png", url);
            verify(s3Service, never()).uploadFile(any(), anyString());
            verify(txCompensation, never()).runAfterCommit(anyString(), any(Runnable.class));
        }

        // UTCID05 - Normal: entity chưa có ảnh và cũng không gửi file
        @Test
        void resolveImageUrl_noCurrentNoNewFile_returnsNull() throws Exception {
            String url = entityImageService.resolveImageUrl(null, null, FOLDER);

            assertNull(url);
            verify(s3Service, never()).uploadFile(any(), anyString());
            verify(txCompensation, never()).runAfterCommit(anyString(), any(Runnable.class));
        }

        // UTCID06 - Abnormal: file không hợp lệ thì không lên lịch xóa ảnh cũ
        @Test
        void resolveImageUrl_invalidFile_keepsOldImageIntact() throws Exception {
            assertThrows(BusinessException.class, () -> entityImageService.resolveImageUrl(
                    "https://s3/tags/old.png", file("doc.pdf", "application/pdf", 1024), FOLDER));

            verify(txCompensation, never()).runAfterCommit(anyString(), any(Runnable.class));
            verify(s3Service, never()).safeDeleteByUrl(anyString());
        }
    }
}
