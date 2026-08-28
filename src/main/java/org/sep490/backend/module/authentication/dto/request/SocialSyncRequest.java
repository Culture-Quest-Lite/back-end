package org.sep490.backend.module.authentication.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SocialSyncRequest {
    @NotBlank(message = "Provider không được để trống")
    private String provider;
}
