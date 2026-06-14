package org.sep490.backend.module.social.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectPostRequest {
    @NotBlank(message = "Lý do từ chối không được để trống")
    private String rejectReason;
}
