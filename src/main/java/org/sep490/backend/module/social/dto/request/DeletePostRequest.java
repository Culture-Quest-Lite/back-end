package org.sep490.backend.module.social.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeletePostRequest {
    @NotBlank(message = "Lý do xóa nội dung không được để trống")
    private String reason;
}
