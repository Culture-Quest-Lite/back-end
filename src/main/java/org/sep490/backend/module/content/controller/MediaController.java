package org.sep490.backend.module.content.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sep490.backend.module.content.dto.request.MediaUploadRequest;
import org.sep490.backend.module.content.dto.response.MediaResponse;
import org.sep490.backend.module.content.service.inter.MediaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadMedia(
            @ModelAttribute @Valid MediaUploadRequest request
    ) {
        try {
            List<MediaResponse> responses = mediaService.uploadAndSaveMedias(
                    request.getFiles(),
                    request.getEntityType(),
                    request.getEntityId()
            );
            return ResponseEntity.ok(responses);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Có lỗi xảy ra khi upload file: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMedia(@PathVariable Long id) {
        mediaService.deleteMedia(id);
        return ResponseEntity.ok("Xóa media thành công!");
    }
}
