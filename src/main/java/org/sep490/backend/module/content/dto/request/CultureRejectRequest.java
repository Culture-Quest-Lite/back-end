package org.sep490.backend.module.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CultureRejectRequest {

    @NotBlank(message = "Lý do từ chối không được để trống")
    String rejectReason;
}
