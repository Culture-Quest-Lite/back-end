package org.sep490.backend.module.user.service;

import org.sep490.backend.module.user.dto.response.LeaderboardPageCache;

import java.time.LocalDateTime;

/**
 * Cache bảng xếp hạng XP.
 *
 * Vì sao không dùng Redis ZSET: câu SQL findLeaderboardByXp sắp theo
 * totalXp DESC, createdAt ASC, userId ASC. ZSET chỉ có MỘT score kiểu double nên
 * không tái tạo được 3 tầng tie-break đó — thứ hạng ở /leaderboard/me sẽ lệch với
 * danh sách. Cache TTL 60 giây đơn giản hơn và luôn khớp 100%.
 */
public interface LeaderboardCacheService {

    /**
     * Một trang bảng xếp hạng.
     *
     * Các entry KHÔNG chứa isCurrentUser — field đó phụ thuộc người xem nên phải
     * gán sau khi lấy từ cache, nếu không user A sẽ thấy highlight của user B.
     */
    LeaderboardPageCache loadPage(int page, int size);

    /**
     * Số người xếp trên user này.
     *
     * xp nằm trong cache key vì hạng đổi ngay khi user được cộng XP — nếu chỉ key
     * theo userId thì sau khi ăn XP vẫn thấy hạng cũ suốt 60 giây.
     */
    long countRankedAbove(Long userId, int xp, LocalDateTime createdAt);

    /** Tổng số người tham gia bảng xếp hạng — dùng chung cho mọi user. */
    long countParticipants();
}
