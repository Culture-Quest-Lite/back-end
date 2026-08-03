package org.sep490.backend.module.content.service.inter;

import org.springframework.web.multipart.MultipartFile;


public interface ImageService {
    String uploadImage(MultipartFile file, String folder);
    String resolveImageUrl(String currentUrl, MultipartFile newFile, String folder);
}
