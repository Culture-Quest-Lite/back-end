package org.sep490.backend.module.user.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MyLeaderboardRankResponse {

    /** Vị trí của người đang đăng nhập, cùng cấu trúc với một dòng trong bảng xếp hạng. */
    LeaderboardEntryResponse entry;

    /** Tổng số người tham gia xếp hạng, để hiển thị dạng "hạng 142/153". */
    Long totalParticipants;
}
