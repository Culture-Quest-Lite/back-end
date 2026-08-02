package org.sep490.backend.module.content.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.service.TransactionCompensationService;
import org.sep490.backend.module.content.service.inter.ImageService;
import org.sep490.backend.module.content.service.inter.S3Service;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ImageServiceImpl implements ImageService {

    static final double MAX_IMAGE_SIZE_MB = 1.0;

    S3Service s3Service;
    TransactionCompensationService txCompensation;

    @Override
    public String uploadImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File không được trống");
        }
        String mimeType = file.getContentType();
        if (mimeType == null || !mimeType.toLowerCase().startsWith("image/")) {
            throw new BusinessException("File tải lên phải là ảnh");
        }
        double fileSizeMb = (double) file.getSize() / (1024 * 1024);
        if (fileSizeMb > MAX_IMAGE_SIZE_MB) {
            throw new BusinessException(
                    String.format("Ảnh '%s' vượt quá dung lượng cho phép (%.1fMB). Tối đa: %.0fMB",
                            file.getOriginalFilename(), fileSizeMb, MAX_IMAGE_SIZE_MB));
        }
        try {
            return s3Service.uploadFile(file, folder);
        } catch (IOException e) {
            throw new BusinessException("Lỗi tải ảnh lên S3: " + e.getMessage());
        }
    }

    @Override
    public String resolveImageUrl(String currentUrl, MultipartFile newFile, String folder) {
        if (newFile == null || newFile.isEmpty()) {
            return currentUrl;
        }
        String newUrl = uploadImage(newFile, folder);

        if (currentUrl != null && !currentUrl.equals(newUrl)) {
            txCompensation.runAfterCommit("Xóa ảnh cũ " + currentUrl,
                    () -> s3Service.safeDeleteByUrl(currentUrl));
        }
        return newUrl;
    }
}
