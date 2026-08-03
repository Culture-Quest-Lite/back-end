package org.sep490.backend.module.user.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LeaderboardEntryResponse {

    /** Thứ hạng tính theo vị trí trong danh sách, bắt đầu từ 1. */
    Integer rank;

    Long userId;
    String username;
    String displayName;
    String avatarUrl;
    Integer totalXp;
    String levelName;

    /** True nếu dòng này là người đang đăng nhập, để client tô sáng. Null khi chưa đăng nhập. */
    Boolean isCurrentUser;
}
