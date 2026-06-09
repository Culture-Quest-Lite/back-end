package org.sep490.backend.module.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FollowUserResponse {
    private Long userId;
    private String username;
    private String displayName;
    private String avatarUrl;
    private String levelName;
}
