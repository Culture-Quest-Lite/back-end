package org.sep490.backend.module.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.sep490.backend.module.user.entity.enumeration.UserRole;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileResponse {
    private Long userId;
    private String username;
    private String email;
    private String displayName;
    private String avatarUrl;
    private Integer totalXp;
    private Integer totalPoints;
    private Boolean autoPlayAudio;
    private Boolean isPremium;
    private UserStatus status;
    private String levelName;
    private UserRole role;
    private LocalDateTime createdAt;
}
