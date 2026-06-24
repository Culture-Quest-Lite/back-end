package org.sep490.backend.module.content.service.impl;

import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.content.service.inter.S3Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3ServiceImpl implements S3Service {
    private final S3Client s3Client;

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
        return s3Client.utilities().getUrl(builder -> builder
                .bucket(bucketName)
                .key(key))
                .toExternalForm();
    }
}
