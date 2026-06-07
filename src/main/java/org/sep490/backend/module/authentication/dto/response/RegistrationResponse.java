package org.sep490.backend.module.authentication.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationResponse {
    private Long userId;
    private String keycloakUserId;
    private String username;
    private String email;
    private String displayName;
    private UserStatus status;
    private String avatarUrl;
    private Integer totalPoints;
    private Boolean autoPlayAudio;
    private Boolean isPremium;
}
