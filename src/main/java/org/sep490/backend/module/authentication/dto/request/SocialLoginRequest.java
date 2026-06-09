package org.sep490.backend.module.authentication.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SocialLoginRequest {
    @NotBlank(message = "Code không được để trống")
    private String code;

    @NotBlank(message = "Redirect URI không được để trống")
    private String redirectUri;
}
