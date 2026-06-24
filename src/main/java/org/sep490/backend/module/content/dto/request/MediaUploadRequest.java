package org.sep490.backend.module.content.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.sep490.backend.module.content.enums.MediaTargetType;
import org.springframework.web.multipart.MultipartFile;

@Data
public class MediaUploadRequest {
    @NotNull(message = "Files không được để trống")
    private MultipartFile[] files;

    @NotNull(message = "Entity type không được để trống")
    private MediaTargetType entityType;

    @NotNull(message = "Entity ID không được để trống")
    private Long entityId;
}
