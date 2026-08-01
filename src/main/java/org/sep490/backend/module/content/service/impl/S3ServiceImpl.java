package org.sep490.backend.module.content.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.common.service.TransactionCompensationService;
import org.sep490.backend.module.content.service.inter.S3Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3ServiceImpl implements S3Service {
    private final S3Client s3Client;
    private final TransactionCompensationService txCompensation;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Override
    public String uploadFile(MultipartFile file, String folder) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String safeFilename = originalFilename != null ? originalFilename.replaceAll("\\s+", "_") : "file";
        String uniqueFilename = UUID.randomUUID() + "_" + safeFilename;
        String key = (folder != null && !folder.trim().isEmpty()) ? folder.trim() + "/" + uniqueFilename : uniqueFilename;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putObjectRequest,
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        // Nếu transaction rollback, object vừa upload sẽ mồ côi trên S3
        txCompensation.runOnRollback("Xóa S3 object " + key, () -> deleteFile(key));

        return s3Client.utilities().getUrl(builder -> builder
                .bucket(bucketName)
                .key(key))
                .toExternalForm();
    }

    @Override
    public void deleteFile(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
        log.info("Đã xóa S3 object: {}", key);
    }

    @Override
    public void safeDeleteByUrl(String fileUrl) {
        String key = extractKeyFromUrl(fileUrl);
        if (key == null) {
            log.warn("Không trích xuất được S3 key từ URL: {}", fileUrl);
            return;
        }
        try {
            deleteFile(key);
        } catch (Exception e) {
            log.error("Không thể xóa S3 object theo URL: {} — cần xóa thủ công", fileUrl, e);
        }
    }

    private String extractKeyFromUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }
        try {
            String path = URI.create(fileUrl).getPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            String key = URLDecoder.decode(path.substring(1), StandardCharsets.UTF_8);
            // Path-style URL (s3.region.amazonaws.com/<bucket>/<key>) có thêm tên bucket ở đầu
            if (key.startsWith(bucketName + "/")) {
                key = key.substring(bucketName.length() + 1);
            }
            return key.isBlank() ? null : key;
        } catch (Exception e) {
            log.warn("URL S3 không hợp lệ: {}", fileUrl, e);
            return null;
        }
    }
}
