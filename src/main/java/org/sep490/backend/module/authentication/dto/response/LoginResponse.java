package org.sep490.backend.module.authentication.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String tokenType;
    private Long expiresIn;

    @JsonIgnore
    private String refreshToken;

    @JsonIgnore
    private Long refreshExpiresIn;
}
