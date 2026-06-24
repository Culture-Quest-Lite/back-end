package org.sep490.backend.module.content.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MediaResponse {
    Long mediaId;
    String mediaType;
    String mimeType;
    String fileUrl;
    String fileName;
    Double fileSize;
    Double duration;
    Integer displayOrder;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
