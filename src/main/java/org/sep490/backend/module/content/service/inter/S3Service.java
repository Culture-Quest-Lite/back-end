package org.sep490.backend.module.content.service.inter;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface S3Service {
    String uploadFile(MultipartFile file, String folder) throws IOException;

    void deleteFile(String key);

    void safeDeleteByUrl(String fileUrl);
}

