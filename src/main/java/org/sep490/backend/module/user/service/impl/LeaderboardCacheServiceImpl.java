package org.sep490.backend.module.user.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.config.redis.CacheNames;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.user.dto.response.LeaderboardEntryResponse;
import org.sep490.backend.module.user.dto.response.LeaderboardPageCache;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.sep490.backend.module.user.service.LeaderboardCacheService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tách riêng khỏi UserServiceImpl để @Cacheable đi qua proxy của Spring.
 * Gọi this.loadPage(...) từ trong cùng một class sẽ KHÔNG kích hoạt cache.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LeaderboardCacheServiceImpl implements LeaderboardCacheService {

    /** Trần kích thước trang: chặn client kéo cả bảng xếp hạng trong một lần gọi. */
    static int MAX_PAGE_SIZE = 100;

    UserRepository userRepository;

    @Override
    @Cacheable(value = CacheNames.LEADERBOARD, key = "#page + ':' + #size")
    @Transactional(readOnly = true)
    public LeaderboardPageCache loadPage(int page, int size) {
        if (page < 0) {
            throw new BusinessException("Số trang không được nhỏ hơn 0");
        }
        if (size <= 0) {
            throw new BusinessException("Kích thước trang phải lớn hơn 0");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new BusinessException("Kích thước trang không được vượt quá 100");
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<User> result = userRepository.findLeaderboardByXp(
                UserStatus.ACTIVE, UserRole.EXPLORER, pageable);

        List<User> users = result.getContent();
        List<LeaderboardEntryResponse> entries = new ArrayList<>(users.size());
        int offset = page * size;

        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            LeaderboardEntryResponse entry = new LeaderboardEntryResponse();
            entry.setRank(offset + i + 1);
            entry.setUserId(user.getUserId());
            entry.setUsername(user.getUsername());
            entry.setDisplayName(user.getDisplayName());
            entry.setAvatarUrl(user.getAvatarUrl());
            entry.setTotalXp(user.getTotalXp() != null ? user.getTotalXp() : 0);
            entry.setLevelName(user.getLevel() != null ? user.getLevel().getName() : null);
            // KHÔNG set isCurrentUser ở đây: phụ thuộc người xem, gán sau khi lấy từ cache
            entries.add(entry);
        }

        return LeaderboardPageCache.builder()
                .entries(entries)
                .totalElements(result.getTotalElements())
                .build();
    }

    @Override
    @Cacheable(value = CacheNames.MY_RANK, key = "#userId + ':' + #xp")
    @Transactional(readOnly = true)
    public long countRankedAbove(Long userId, int xp, LocalDateTime createdAt) {
        if (userId == null) {
            throw new BusinessException("Không xác định được người dùng");
        }
        if (xp < 0) {
            throw new BusinessException("Điểm kinh nghiệm không được nhỏ hơn 0");
        }
        if (createdAt == null) {
            throw new BusinessException("Thiếu thời điểm tạo tài khoản để xếp hạng");
        }
        return userRepository.countUsersRankedAbove(
                UserStatus.ACTIVE, UserRole.EXPLORER, xp, createdAt, userId);
    }

    @Override
    @Cacheable(value = CacheNames.MY_RANK, key = "'total'")
    @Transactional(readOnly = true)
    public long countParticipants() {
        return userRepository.countByStatusAndRole(UserStatus.ACTIVE, UserRole.EXPLORER);
    }
}
