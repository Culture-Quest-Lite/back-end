package org.sep490.backend.module.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FollowStatusResponse {
    private Long userId;
    private Boolean isFollowing;
    private Long totalFollowers;
    private String message;
}
