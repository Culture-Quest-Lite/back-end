package org.sep490.backend.module.authentication.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MobileLoginResponse {
    private String accessToken;
    private String tokenType;
    private Long expiresIn;
    private String refreshToken;
    private Long refreshExpiresIn;
}
