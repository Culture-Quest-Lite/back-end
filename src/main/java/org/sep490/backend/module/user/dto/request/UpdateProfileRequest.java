package org.sep490.backend.module.user.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UpdateProfileRequest {

    @Size(max = 20, message = "Tên hiển thị không được vượt quá 20 ký tự")
    private String displayName;

    private MultipartFile avatarFile;

    private MultipartFile backgroundFile;

    private Boolean autoPlayAudio;
}

