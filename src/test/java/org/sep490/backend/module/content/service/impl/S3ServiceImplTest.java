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
import org.sep490.backend.common.service.TransactionCompensationService;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.net.URL;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho LƯU TRỮ TỆP TRÊN S3 (upload có bù trừ rollback, xóa an toàn theo URL).
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class S3ServiceImplTest {

    private static final String BUCKET = "culturequest-media";

    @Mock private S3Client s3Client;
    @Mock private S3Utilities s3Utilities;
    @Mock private TransactionCompensationService txCompensation;

    @InjectMocks private S3ServiceImpl s3Service;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(s3Service, "bucketName", BUCKET);
        when(s3Client.utilities()).thenReturn(s3Utilities);
        when(s3Utilities.getUrl(any(Consumer.class))).thenReturn(
                new URL("https://culturequest-media.s3.ap-southeast-1.amazonaws.com/hotspots/anh.jpg"));
    }

    private static MockMultipartFile file(String originalName) {
        return new MockMultipartFile("file", originalName, "image/jpeg",
                "noi-dung-anh".getBytes());
    }

    // =====================================================================
    // Function: uploadFile
    // =====================================================================
    @Nested
    @DisplayName("uploadFile")
    class UploadFileTest {

        // UTCID01 - Normal: upload vào thư mục "hotspots" -> key có tiền tố thư mục
        @Test
        void uploadFile_withFolder_prefixesKeyWithFolder() throws IOException {
            s3Service.uploadFile(file("van-mieu.jpg"), "hotspots");

            ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
            assertEquals(BUCKET, captor.getValue().bucket());
            assertTrue(captor.getValue().key().startsWith("hotspots/"));
            assertTrue(captor.getValue().key().endsWith("_van-mieu.jpg"));
            assertEquals("image/jpeg", captor.getValue().contentType());
        }

        // UTCID02 - Boundary: không truyền thư mục (null) -> key nằm ở gốc bucket
        @Test
        void uploadFile_nullFolder_keyAtBucketRoot() throws IOException {
            s3Service.uploadFile(file("van-mieu.jpg"), null);

            ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
            assertFalse(captor.getValue().key().contains("/"));
        }

        // UTCID03 - Boundary: thư mục là chuỗi trắng -> coi như không có thư mục
        @Test
        void uploadFile_blankFolder_keyAtBucketRoot() throws IOException {
            s3Service.uploadFile(file("van-mieu.jpg"), "   ");

            ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
            assertFalse(captor.getValue().key().contains("/"));
        }

        // UTCID04 - Boundary: tên tệp có khoảng trắng -> thay bằng "_" để URL không bị vỡ
        @Test
        void uploadFile_filenameWithSpaces_replacedByUnderscore() throws IOException {
            s3Service.uploadFile(file("van mieu quoc tu giam.jpg"), "hotspots");

            ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
            assertTrue(captor.getValue().key().endsWith("_van_mieu_quoc_tu_giam.jpg"));
            assertFalse(captor.getValue().key().contains(" "));
        }

        // UTCID05 - Normal: mỗi lần upload sinh key duy nhất (UUID) dù cùng tên tệp
        @Test
        void uploadFile_sameFilenameTwice_generatesDifferentKeys() throws IOException {
            s3Service.uploadFile(file("van-mieu.jpg"), "hotspots");
            s3Service.uploadFile(file("van-mieu.jpg"), "hotspots");

            ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client, times(2)).putObject(captor.capture(), any(RequestBody.class));
            assertNotEquals(captor.getAllValues().get(0).key(), captor.getAllValues().get(1).key());
        }

        // UTCID06 - Normal: đăng ký dọn dẹp để nếu transaction rollback thì xóa tệp mồ côi
        @Test
        void uploadFile_registersRollbackCompensation() throws IOException {
            s3Service.uploadFile(file("van-mieu.jpg"), "hotspots");

            verify(txCompensation).runOnRollback(startsWith("Xóa S3 object hotspots/"), any(Runnable.class));
        }

        // UTCID07 - Normal: trả về URL công khai của tệp vừa upload
        @Test
        void uploadFile_returnsPublicUrl() throws IOException {
            String url = s3Service.uploadFile(file("van-mieu.jpg"), "hotspots");

            assertEquals("https://culturequest-media.s3.ap-southeast-1.amazonaws.com/hotspots/anh.jpg", url);
        }
    }

    // =====================================================================
    // Function: safeDeleteByUrl
    // =====================================================================
    @Nested
    @DisplayName("safeDeleteByUrl")
    class SafeDeleteByUrlTest {

        // UTCID01 - Normal: URL virtual-hosted -> lấy đúng key sau tên miền
        @Test
        void safeDeleteByUrl_virtualHostedUrl_deletesCorrectKey() {
            s3Service.safeDeleteByUrl(
                    "https://culturequest-media.s3.ap-southeast-1.amazonaws.com/hotspots/van-mieu.jpg");

            ArgumentCaptor<DeleteObjectRequest> captor =
                    ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(captor.capture());
            assertEquals("hotspots/van-mieu.jpg", captor.getValue().key());
            assertEquals(BUCKET, captor.getValue().bucket());
        }

        // UTCID02 - Boundary: URL path-style có tên bucket ở đầu -> phải cắt bỏ tên bucket
        @Test
        void safeDeleteByUrl_pathStyleUrl_stripsBucketPrefix() {
            s3Service.safeDeleteByUrl(
                    "https://s3.ap-southeast-1.amazonaws.com/culturequest-media/hotspots/van-mieu.jpg");

            ArgumentCaptor<DeleteObjectRequest> captor =
                    ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(captor.capture());
            assertEquals("hotspots/van-mieu.jpg", captor.getValue().key());
        }

        // UTCID03 - Boundary: key có ký tự đã mã hóa URL -> giải mã trước khi xóa
        @Test
        void safeDeleteByUrl_encodedKey_isDecoded() {
            s3Service.safeDeleteByUrl(
                    "https://culturequest-media.s3.amazonaws.com/hotspots/van%20mieu.jpg");

            ArgumentCaptor<DeleteObjectRequest> captor =
                    ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(captor.capture());
            assertEquals("hotspots/van mieu.jpg", captor.getValue().key());
        }

        // UTCID04 - Abnormal: URL null -> bỏ qua, không gọi S3
        @Test
        void safeDeleteByUrl_nullUrl_doesNothing() {
            s3Service.safeDeleteByUrl(null);

            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        }

        // UTCID05 - Abnormal: URL rỗng -> bỏ qua, không gọi S3
        @Test
        void safeDeleteByUrl_blankUrl_doesNothing() {
            s3Service.safeDeleteByUrl("   ");

            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        }

        // UTCID06 - Abnormal: URL không có phần path -> không xác định được key, bỏ qua
        @Test
        void safeDeleteByUrl_urlWithoutPath_doesNothing() {
            s3Service.safeDeleteByUrl("https://culturequest-media.s3.amazonaws.com");

            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        }

        // UTCID07 - Abnormal: S3 ném lỗi khi xóa -> nuốt lỗi, không phá luồng nghiệp vụ chính
        @Test
        void safeDeleteByUrl_s3Throws_exceptionIsSwallowed() {
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenThrow(S3Exception.builder().message("Access Denied").build());

            assertDoesNotThrow(() -> s3Service.safeDeleteByUrl(
                    "https://culturequest-media.s3.amazonaws.com/hotspots/van-mieu.jpg"));
        }
    }

    // =====================================================================
    // Function: deleteFile
    // =====================================================================
    @Nested
    @DisplayName("deleteFile")
    class DeleteFileTest {

        // UTCID01 - Normal: xóa theo key -> gọi S3 với đúng bucket và key
        @Test
        void deleteFile_validKey_callsS3WithBucketAndKey() {
            s3Service.deleteFile("hotspots/van-mieu.jpg");

            ArgumentCaptor<DeleteObjectRequest> captor =
                    ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(captor.capture());
            assertEquals(BUCKET, captor.getValue().bucket());
            assertEquals("hotspots/van-mieu.jpg", captor.getValue().key());
        }

        // UTCID02 - Abnormal: S3 ném lỗi -> lỗi được ném ra ngoài để caller quyết định xử lý
        @Test
        void deleteFile_s3Throws_propagatesException() {
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenThrow(S3Exception.builder().message("Access Denied").build());

            assertThrows(S3Exception.class, () -> s3Service.deleteFile("hotspots/van-mieu.jpg"));
        }

        // UTCID03 - Boundary: key ở gốc bucket (không có thư mục) -> vẫn xóa được
        @Test
        void deleteFile_rootLevelKey_callsS3() {
            s3Service.deleteFile("van-mieu.jpg");

            ArgumentCaptor<DeleteObjectRequest> captor =
                    ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(captor.capture());
            assertEquals("van-mieu.jpg", captor.getValue().key());
        }
    }
}
