package org.sep490.backend.module.user.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.user.dto.response.LeaderboardPageCache;
import org.sep490.backend.module.user.entity.Level;
import org.sep490.backend.module.user.entity.enumeration.LevelStatus;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho BẢNG XẾP HẠNG (tính thứ hạng theo XP, dữ liệu được cache theo trang).
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaderboardCacheServiceImplTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private LeaderboardCacheServiceImpl leaderboardCacheService;

    private static User explorer(Long userId, String username, String displayName,
                                 Integer totalXp, Level level) {
        User user = new User();
        user.setUserId(userId);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setAvatarUrl("https://cdn.culturequest.vn/avatar/" + userId + ".png");
        user.setTotalXp(totalXp);
        user.setLevel(level);
        return user;
    }

    private static Level kyCuu() {
        return Level.builder().levelId(2L).name("Kỳ Cựu").requiredXp(1000)
                .status(LevelStatus.ACTIVE).build();
    }

    // =====================================================================
    // Function: loadPage
    // =====================================================================
    @Nested
    @DisplayName("loadPage")
    class LoadPageTest {

        // UTCID01 - Normal: trang đầu -> thứ hạng đánh số từ 1
        @Test
        void loadPage_firstPage_ranksStartFromOne() {
            when(userRepository.findLeaderboardByXp(any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(
                            explorer(1L, "hunggg", "Hưng", 5000, kyCuu()),
                            explorer(2L, "minhanh", "Minh Anh", 3200, kyCuu())),
                            PageRequest.of(0, 10), 2));

            LeaderboardPageCache result = leaderboardCacheService.loadPage(0, 10);

            assertEquals(1, result.getEntries().get(0).getRank());
            assertEquals(2, result.getEntries().get(1).getRank());
            assertEquals(2, result.getTotalElements());
        }

        // UTCID02 - Boundary: trang thứ 2 (size 10) -> thứ hạng bắt đầu từ 11
        @Test
        void loadPage_secondPage_ranksContinueWithOffset() {
            when(userRepository.findLeaderboardByXp(any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(
                            explorer(11L, "user11", "Người 11", 900, null)),
                            PageRequest.of(1, 10), 11));

            LeaderboardPageCache result = leaderboardCacheService.loadPage(1, 10);

            assertEquals(11, result.getEntries().get(0).getRank());
        }

        // UTCID03 - Normal: chỉ lấy người dùng ACTIVE có vai trò EXPLORER
        @Test
        void loadPage_queriesOnlyActiveExplorers() {
            when(userRepository.findLeaderboardByXp(any(), any(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            leaderboardCacheService.loadPage(0, 10);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(userRepository).findLeaderboardByXp(
                    eq(UserStatus.ACTIVE), eq(UserRole.EXPLORER), captor.capture());
            assertEquals(0, captor.getValue().getPageNumber());
            assertEquals(10, captor.getValue().getPageSize());
        }

        // UTCID04 - Boundary: user chưa có XP (null) -> hiển thị 0 thay vì null
        @Test
        void loadPage_nullTotalXp_displaysZero() {
            when(userRepository.findLeaderboardByXp(any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(
                            explorer(1L, "newbie", "Tân Binh", null, null)),
                            PageRequest.of(0, 10), 1));

            LeaderboardPageCache result = leaderboardCacheService.loadPage(0, 10);

            assertEquals(0, result.getEntries().get(0).getTotalXp());
        }

        // UTCID05 - Boundary: user chưa có cấp bậc -> tên cấp bậc là null, không lỗi
        @Test
        void loadPage_nullLevel_levelNameIsNull() {
            when(userRepository.findLeaderboardByXp(any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(
                            explorer(1L, "newbie", "Tân Binh", 0, null)),
                            PageRequest.of(0, 10), 1));

            assertNull(leaderboardCacheService.loadPage(0, 10).getEntries().get(0).getLevelName());
        }

        // UTCID06 - Normal: map đầy đủ thông tin hiển thị (username, tên, avatar, cấp bậc)
        @Test
        void loadPage_mapsAllDisplayFields() {
            when(userRepository.findLeaderboardByXp(any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(
                            explorer(1L, "hunggg", "Hưng", 5000, kyCuu())),
                            PageRequest.of(0, 10), 1));

            var entry = leaderboardCacheService.loadPage(0, 10).getEntries().get(0);

            assertEquals(1L, entry.getUserId());
            assertEquals("hunggg", entry.getUsername());
            assertEquals("Hưng", entry.getDisplayName());
            assertEquals("https://cdn.culturequest.vn/avatar/1.png", entry.getAvatarUrl());
            assertEquals(5000, entry.getTotalXp());
            assertEquals("Kỳ Cựu", entry.getLevelName());
        }

        // UTCID07 - Boundary: chưa có người dùng nào -> danh sách rỗng, tổng = 0
        @Test
        void loadPage_noUsers_returnsEmptyEntries() {
            when(userRepository.findLeaderboardByXp(any(), any(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            LeaderboardPageCache result = leaderboardCacheService.loadPage(0, 10);

            assertTrue(result.getEntries().isEmpty());
            assertEquals(0, result.getTotalElements());
        }

        // UTCID08 - Abnormal: page = -1 -> chặn trước khi dựng Pageable
        @Test
        void loadPage_negativePage_throwsInvalidPage() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> leaderboardCacheService.loadPage(-1, 10));

            assertEquals("Số trang không được nhỏ hơn 0", ex.getMessage());
            verifyNoInteractions(userRepository);
        }

        // UTCID09 - Boundary: size = 0 -> kích thước trang phải lớn hơn 0
        @Test
        void loadPage_zeroSize_throwsInvalidSize() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> leaderboardCacheService.loadPage(0, 0));

            assertEquals("Kích thước trang phải lớn hơn 0", ex.getMessage());
            verifyNoInteractions(userRepository);
        }

        // UTCID10 - Boundary: size = 101 -> vượt trần 100 bản ghi mỗi trang
        @Test
        void loadPage_sizeOverLimit_throwsSizeExceeded() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> leaderboardCacheService.loadPage(0, 101));

            assertEquals("Kích thước trang không được vượt quá 100", ex.getMessage());
            verifyNoInteractions(userRepository);
        }
    }

    // =====================================================================
    // Function: countRankedAbove / countParticipants
    // =====================================================================
    @Nested
    @DisplayName("countRankedAbove")
    class CountRankedAboveTest {

        // UTCID01 - Normal: có 24 người trên mình -> hạng của mình là 25
        @Test
        void countRankedAbove_hasUsersAbove_returnsCount() {
            LocalDateTime createdAt = LocalDateTime.of(2026, 1, 15, 8, 0);
            when(userRepository.countUsersRankedAbove(
                    UserStatus.ACTIVE, UserRole.EXPLORER, 1200, createdAt, 1L)).thenReturn(24L);

            assertEquals(24L, leaderboardCacheService.countRankedAbove(1L, 1200, createdAt));
        }

        // UTCID02 - Boundary: đang đứng đầu -> không ai trên mình, trả 0
        @Test
        void countRankedAbove_topUser_returnsZero() {
            LocalDateTime createdAt = LocalDateTime.of(2026, 1, 15, 8, 0);
            when(userRepository.countUsersRankedAbove(any(), any(), anyInt(), any(), anyLong()))
                    .thenReturn(0L);

            assertEquals(0L, leaderboardCacheService.countRankedAbove(1L, 99999, createdAt));
        }

        // UTCID03 - Boundary: XP = 0 (mới đăng ký) -> vẫn tính hạng, không lỗi
        @Test
        void countRankedAbove_zeroXp_stillCounts() {
            LocalDateTime createdAt = LocalDateTime.of(2026, 8, 12, 10, 0);
            when(userRepository.countUsersRankedAbove(any(), any(), eq(0), any(), anyLong()))
                    .thenReturn(150L);

            assertEquals(150L, leaderboardCacheService.countRankedAbove(1L, 0, createdAt));
        }

        // UTCID04 - Normal: tổng số người tham gia chỉ đếm EXPLORER đang ACTIVE
        @Test
        void countParticipants_countsOnlyActiveExplorers() {
            when(userRepository.countByStatusAndRole(UserStatus.ACTIVE, UserRole.EXPLORER))
                    .thenReturn(1250L);

            assertEquals(1250L, leaderboardCacheService.countParticipants());
            verify(userRepository).countByStatusAndRole(UserStatus.ACTIVE, UserRole.EXPLORER);
        }

        // UTCID05 - Abnormal: userId = null -> không xác định được người dùng
        @Test
        void countRankedAbove_nullUserId_throwsUserNotIdentified() {
            LocalDateTime createdAt = LocalDateTime.of(2026, 1, 15, 8, 0);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> leaderboardCacheService.countRankedAbove(null, 1200, createdAt));

            assertEquals("Không xác định được người dùng", ex.getMessage());
            verifyNoInteractions(userRepository);
        }

        // UTCID06 - Abnormal: xp = -1 -> điểm kinh nghiệm không hợp lệ
        @Test
        void countRankedAbove_negativeXp_throwsInvalidXp() {
            LocalDateTime createdAt = LocalDateTime.of(2026, 1, 15, 8, 0);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> leaderboardCacheService.countRankedAbove(1L, -1, createdAt));

            assertEquals("Điểm kinh nghiệm không được nhỏ hơn 0", ex.getMessage());
            verifyNoInteractions(userRepository);
        }

        // UTCID07 - Abnormal: createdAt = null -> thiếu mốc phá hoà khi xếp hạng
        @Test
        void countRankedAbove_nullCreatedAt_throwsMissingCreatedAt() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> leaderboardCacheService.countRankedAbove(1L, 1200, null));

            assertEquals("Thiếu thời điểm tạo tài khoản để xếp hạng", ex.getMessage());
            verifyNoInteractions(userRepository);
        }
    }
}
