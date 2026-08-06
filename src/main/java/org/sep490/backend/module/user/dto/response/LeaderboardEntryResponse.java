package org.sep490.backend.module.user.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LeaderboardEntryResponse {

    Integer rank;

    Long userId;
    String username;
    String displayName;
    String avatarUrl;
    Integer totalXp;
    String levelName;

    Boolean isCurrentUser;
}
