package org.sep490.backend.module.content.service.inter;

import org.sep490.backend.module.content.dto.response.MediaResponse;
import org.sep490.backend.module.content.enums.MediaTargetType;
import org.sep490.backend.module.content.enums.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface MediaService {
    List<MediaResponse> uploadAndSaveMedias(MultipartFile[] files, MediaTargetType mediaType, Long entityId) throws IOException;
    void deleteMedia(Long mediaId);
}
