package org.sep490.backend.module.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeviceTokenRequest {
    @NotBlank(message = "Token không được để trống")
    private String token;
}
