package org.sep490.backend.module.user.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Dữ liệu một trang bảng xếp hạng được lưu trong Redis.
 *
 * KHÔNG cache Page<T> trực tiếp vì PageImpl không deserialize được từ JSON;
 * cache List + totalElements rồi dựng lại PageImpl ở tầng service.
 *
 * Lưu ý: các entry ở đây KHÔNG chứa isCurrentUser — field đó phụ thuộc người đang
 * xem nên phải gán sau khi lấy từ cache, nếu không user A sẽ thấy highlight của user B.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LeaderboardPageCache {

    List<LeaderboardEntryResponse> entries;

    long totalElements;
}
