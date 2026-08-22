package org.sep490.backend.module.gamification.service.impl;

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
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.gamification.dto.request.RewardTransactionRequest;
import org.sep490.backend.module.gamification.dto.response.RewardTransactionResponse;
import org.sep490.backend.module.gamification.entity.RewardTransaction;
import org.sep490.backend.module.gamification.entity.enumeration.TransactionType;
import org.sep490.backend.module.gamification.mapper.RewardTransactionMapper;
import org.sep490.backend.module.gamification.repository.RewardTransactionRepository;
import org.sep490.backend.module.notification.entity.enumeration.NotificationType;
import org.sep490.backend.module.notification.service.FcmService;
import org.sep490.backend.module.partner.dto.filter.VoucherFilter;
import org.sep490.backend.module.user.entity.Level;
import org.sep490.backend.module.user.entity.LevelProgress;
import org.sep490.backend.module.user.entity.enumeration.LevelStatus;
import org.sep490.backend.module.user.repository.LevelProgressRepository;
import org.sep490.backend.module.user.repository.LevelRepository;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho GIAO DỊCH ĐIỂM THƯỞNG / KINH NGHIỆM (cộng điểm, lên cấp, chống sai lệch số dư).
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RewardTransactionServiceImplTest {

    @Mock private UserService userService;
    @Mock private UserRepository userRepository;
    @Mock private RewardTransactionRepository rewardTransactionRepository;
    @Mock private RewardTransactionMapper rewardTransactionMapper;
    @Mock private LevelRepository levelRepository;
    @Mock private LevelProgressRepository levelProgressRepository;
    @Mock private FcmService fcmService;

    @InjectMocks private RewardTransactionServiceImpl rewardTransactionService;

    private static Level level(Long id, String name, int requiredXp) {
        return Level.builder()
                .levelId(id)
                .name(name)
                .requiredXp(requiredXp)
                .status(LevelStatus.ACTIVE)
                .build();
    }

    private static User user(Long userId, int totalPoints, int totalXp, Level level) {
        User user = new User();
        user.setUserId(userId);
        user.setTotalPoints(totalPoints);
        user.setTotalXp(totalXp);
        user.setLevel(level);
        user.setFcmToken("fcm-token-explorer-01");
        return user;
    }

    private static RewardTransaction lastTx(long pointsBalance, long xpBalance) {
        return RewardTransaction.builder()
                .transactionId(99L)
                .pointsBalance(pointsBalance)
                .xpBalance(xpBalance)
                .build();
    }

    private static RewardTransactionRequest request(Long userId, Long points, Long xp) {
        return RewardTransactionRequest.builder()
                .userId(userId)
                .pointsAmount(points)
                .xpAmount(xp)
                .transactionType(TransactionType.HOTSPOT_CHECKIN)
                .description("Check-in Văn Miếu Quốc Tử Giám")
                .build();
    }

    // =====================================================================
    // Function: createRewardTransaction
    // =====================================================================
    @Nested
    @DisplayName("createRewardTransaction")
    class CreateRewardTransactionTest {

        // UTCID01 - Normal: cộng 50 điểm + 100 XP cho user chưa có giao dịch nào
        @Test
        void createRewardTransaction_firstTransaction_addsPointsAndXp() {
            User target = user(1L, 0, 0, null);
            when(userService.getUserById(1L)).thenReturn(target);
            when(rewardTransactionRepository.findFirstByUser_UserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.empty());
            when(levelRepository.findFirstByStatusAndRequiredXpLessThanEqualOrderByRequiredXpDesc(any(), anyInt()))
                    .thenReturn(Optional.empty());
            when(rewardTransactionMapper.toEntity(any(), any())).thenReturn(new RewardTransaction());

            rewardTransactionService.createRewardTransaction(request(1L, 50L, 100L));

            assertEquals(50, target.getTotalPoints());
            assertEquals(100, target.getTotalXp());
            verify(userRepository).save(target);
        }

        // UTCID02 - Boundary: pointsAmount và xpAmount null -> coi như 0, số dư giữ nguyên
        @Test
        void createRewardTransaction_nullAmounts_treatedAsZero() {
            User target = user(1L, 500, 1200, level(2L, "Kỳ Cựu", 1000));
            when(userService.getUserById(1L)).thenReturn(target);
            when(rewardTransactionRepository.findFirstByUser_UserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(lastTx(500L, 1200L)));
            when(rewardTransactionMapper.toEntity(any(), any())).thenReturn(new RewardTransaction());

            rewardTransactionService.createRewardTransaction(request(1L, null, null));

            assertEquals(500, target.getTotalPoints());
            assertEquals(1200, target.getTotalXp());
            verify(levelRepository, never())
                    .findFirstByStatusAndRequiredXpLessThanEqualOrderByRequiredXpDesc(any(), anyInt());
        }

        // UTCID03 - Abnormal: client gửi pointsBalance kỳ vọng sai -> chặn ghi lệch số dư
        @Test
        void createRewardTransaction_pointsBalanceMismatch_throwsPointsMismatch() {
            User target = user(1L, 500, 1200, null);
            when(userService.getUserById(1L)).thenReturn(target);
            when(rewardTransactionRepository.findFirstByUser_UserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(lastTx(500L, 1200L)));

            RewardTransactionRequest request = request(1L, 50L, 0L);
            request.setPointsBalance(999L); // đúng phải là 550

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rewardTransactionService.createRewardTransaction(request));

            assertEquals("Tổng số điểm thưởng hiện tại không khớp với giao dịch trước đó", ex.getMessage());
            verify(userRepository, never()).save(any());
        }

        // UTCID04 - Abnormal: client gửi xpBalance kỳ vọng sai -> chặn ghi lệch XP
        @Test
        void createRewardTransaction_xpBalanceMismatch_throwsXpMismatch() {
            User target = user(1L, 500, 1200, null);
            when(userService.getUserById(1L)).thenReturn(target);
            when(rewardTransactionRepository.findFirstByUser_UserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(lastTx(500L, 1200L)));

            RewardTransactionRequest request = request(1L, 0L, 100L);
            request.setPointsBalance(500L);
            request.setXpBalance(9999L); // đúng phải là 1300

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rewardTransactionService.createRewardTransaction(request));

            assertEquals("Tổng số kinh nghiệm hiện tại không khớp với giao dịch trước đó", ex.getMessage());
            verify(userRepository, never()).save(any());
        }

        // UTCID05 - Abnormal: điểm trên user lệch với giao dịch gần nhất -> dữ liệu hỏng, phải chặn
        @Test
        void createRewardTransaction_userPointsDivergedFromLastTx_throwsNewPointsMismatch() {
            User target = user(1L, 500, 1200, null);
            when(userService.getUserById(1L)).thenReturn(target);
            when(rewardTransactionRepository.findFirstByUser_UserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(lastTx(480L, 1200L)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rewardTransactionService.createRewardTransaction(request(1L, 50L, 0L)));

            assertEquals("Tổng số điểm thưởng mới không khớp", ex.getMessage());
        }

        // UTCID06 - Abnormal: XP trên user lệch với giao dịch gần nhất -> chặn
        @Test
        void createRewardTransaction_userXpDivergedFromLastTx_throwsXpMismatch() {
            User target = user(1L, 500, 1200, null);
            when(userService.getUserById(1L)).thenReturn(target);
            when(rewardTransactionRepository.findFirstByUser_UserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(lastTx(500L, 1100L)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rewardTransactionService.createRewardTransaction(request(1L, 0L, 100L)));

            assertEquals("Tổng số điểm kinh nghiệm không khớp", ex.getMessage());
        }

        // UTCID07 - Normal: đủ XP lên cấp mới -> đổi level, ghi lịch sử và bắn thông báo LEVEL_UP
        @Test
        void createRewardTransaction_enoughXpForNewLevel_upgradesAndNotifies() {
            Level oldLevel = level(1L, "Tân Binh", 0);
            Level newLevel = level(2L, "Kỳ Cựu", 1000);
            User target = user(1L, 0, 900, oldLevel);

            when(userService.getUserById(1L)).thenReturn(target);
            when(rewardTransactionRepository.findFirstByUser_UserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(lastTx(0L, 900L)));
            when(levelRepository.findFirstByStatusAndRequiredXpLessThanEqualOrderByRequiredXpDesc(
                    LevelStatus.ACTIVE, 1000)).thenReturn(Optional.of(newLevel));
            when(levelProgressRepository.existsByUser_UserIdAndLevel_LevelId(1L, 2L)).thenReturn(false);
            when(levelProgressRepository.save(any(LevelProgress.class)))
                    .thenAnswer(inv -> {
                        LevelProgress saved = inv.getArgument(0);
                        saved.setLevelProgressId(77L);
                        return saved;
                    });
            when(rewardTransactionMapper.toEntity(any(), any())).thenReturn(new RewardTransaction());

            rewardTransactionService.createRewardTransaction(request(1L, 0L, 100L));

            assertEquals(newLevel, target.getLevel());
            assertEquals(1000, target.getTotalXp());
            verify(levelProgressRepository).save(any(LevelProgress.class));
            verify(fcmService).sendPushNotification(
                    eq("fcm-token-explorer-01"),
                    eq("Chúc mừng! Bạn đã đạt cấp độ mới: Kỳ Cựu"),
                    anyString(),
                    eq(NotificationType.LEVEL_UP),
                    eq(77L));
        }

        // UTCID08 - Boundary: XP tăng nhưng chưa đủ ngưỡng -> giữ nguyên cấp, không bắn thông báo
        @Test
        void createRewardTransaction_xpBelowNextLevel_keepsCurrentLevel() {
            Level currentLevel = level(1L, "Tân Binh", 0);
            User target = user(1L, 0, 900, currentLevel);

            when(userService.getUserById(1L)).thenReturn(target);
            when(rewardTransactionRepository.findFirstByUser_UserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(lastTx(0L, 900L)));
            when(levelRepository.findFirstByStatusAndRequiredXpLessThanEqualOrderByRequiredXpDesc(
                    LevelStatus.ACTIVE, 950)).thenReturn(Optional.of(currentLevel));
            when(rewardTransactionMapper.toEntity(any(), any())).thenReturn(new RewardTransaction());

            rewardTransactionService.createRewardTransaction(request(1L, 0L, 50L));

            assertEquals(currentLevel, target.getLevel());
            verify(levelProgressRepository, never()).save(any());
            verifyNoInteractions(fcmService);
        }

        // UTCID09 - Boundary: đã có lịch sử cấp bậc này (bị trừ XP rồi lấy lại) -> không bắn thông báo lần 2
        @Test
        void createRewardTransaction_levelAlreadyUnlocked_doesNotNotifyAgain() {
            Level oldLevel = level(1L, "Tân Binh", 0);
            Level newLevel = level(2L, "Kỳ Cựu", 1000);
            User target = user(1L, 0, 900, oldLevel);

            when(userService.getUserById(1L)).thenReturn(target);
            when(rewardTransactionRepository.findFirstByUser_UserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(lastTx(0L, 900L)));
            when(levelRepository.findFirstByStatusAndRequiredXpLessThanEqualOrderByRequiredXpDesc(
                    LevelStatus.ACTIVE, 1000)).thenReturn(Optional.of(newLevel));
            when(levelProgressRepository.existsByUser_UserIdAndLevel_LevelId(1L, 2L)).thenReturn(true);
            when(rewardTransactionMapper.toEntity(any(), any())).thenReturn(new RewardTransaction());

            rewardTransactionService.createRewardTransaction(request(1L, 0L, 100L));

            assertEquals(newLevel, target.getLevel());
            verify(levelProgressRepository, never()).save(any());
            verifyNoInteractions(fcmService);
        }

        // UTCID10 - Normal: giao dịch trừ điểm (đổi voucher) -> ghi số dư mới vào bản ghi giao dịch
        @Test
        void createRewardTransaction_negativePoints_recordsNewBalances() {
            User target = user(1L, 500, 1200, level(2L, "Kỳ Cựu", 1000));
            RewardTransaction saved = new RewardTransaction();

            when(userService.getUserById(1L)).thenReturn(target);
            when(rewardTransactionRepository.findFirstByUser_UserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(lastTx(500L, 1200L)));
            when(rewardTransactionMapper.toEntity(any(), any())).thenReturn(saved);

            RewardTransactionRequest request = request(1L, -200L, 0L);
            request.setTransactionType(TransactionType.REDEEM_VOUCHER);

            rewardTransactionService.createRewardTransaction(request);

            assertEquals(300, target.getTotalPoints());
            assertEquals(-200L, saved.getPointsAmount());
            assertEquals(300L, saved.getPointsBalance());
            assertEquals(1200L, saved.getXpBalance());
            assertSame(target, saved.getUser());
            verify(rewardTransactionRepository).save(saved);
        }
    }

    // =====================================================================
    // Function: getMyRewardHistory
    // =====================================================================
    @Nested
    @DisplayName("getMyRewardHistory")
    class GetMyRewardHistoryTest {

        private VoucherFilter filter(String sortBy, String sortDir, int page, int size) {
            VoucherFilter filter = new VoucherFilter();
            filter.setSortBy(sortBy);
            filter.setSortDir(sortDir);
            filter.setPage(page);
            filter.setSize(size);
            return filter;
        }

        // UTCID01 - Boundary: sortBy = "id" -> quy đổi sang cột thật "createdAt"
        @Test
        void getMyRewardHistory_sortByIdAlias_mapsToCreatedAt() {
            User current = user(1L, 500, 1200, null);
            when(userService.getCurrentUser()).thenReturn(current);
            when(rewardTransactionRepository.findByUser_UserId(eq(1L), any(Pageable.class)))
                    .thenReturn(Page.empty());

            rewardTransactionService.getMyRewardHistory(filter("id", "desc", 0, 10));

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(rewardTransactionRepository).findByUser_UserId(eq(1L), captor.capture());
            assertEquals(Sort.by("createdAt").descending(), captor.getValue().getSort());
        }

        // UTCID02 - Normal: sortDir = "asc" -> sắp xếp tăng dần theo cột yêu cầu
        @Test
        void getMyRewardHistory_sortAsc_appliesAscendingSort() {
            User current = user(1L, 500, 1200, null);
            when(userService.getCurrentUser()).thenReturn(current);
            when(rewardTransactionRepository.findByUser_UserId(eq(1L), any(Pageable.class)))
                    .thenReturn(Page.empty());

            rewardTransactionService.getMyRewardHistory(filter("pointsAmount", "asc", 0, 10));

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(rewardTransactionRepository).findByUser_UserId(eq(1L), captor.capture());
            assertEquals(Sort.by("pointsAmount").ascending(), captor.getValue().getSort());
        }

        // UTCID03 - Normal: trả về đúng số bản ghi đã map sang response
        @Test
        void getMyRewardHistory_hasRecords_returnsMappedPage() {
            User current = user(1L, 500, 1200, null);
            when(userService.getCurrentUser()).thenReturn(current);
            when(rewardTransactionRepository.findByUser_UserId(eq(1L), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(lastTx(500L, 1200L), lastTx(450L, 1100L)),
                            PageRequest.of(0, 10), 2));
            when(rewardTransactionMapper.toResponse(any())).thenReturn(new RewardTransactionResponse());

            Page<RewardTransactionResponse> result =
                    rewardTransactionService.getMyRewardHistory(filter("createdAt", "desc", 0, 10));

            assertEquals(2, result.getTotalElements());
            assertEquals(2, result.getContent().size());
        }

        // UTCID04 - Boundary: user chưa có giao dịch nào -> trang rỗng, không lỗi
        @Test
        void getMyRewardHistory_noRecords_returnsEmptyPage() {
            User current = user(1L, 0, 0, null);
            when(userService.getCurrentUser()).thenReturn(current);
            when(rewardTransactionRepository.findByUser_UserId(eq(1L), any(Pageable.class)))
                    .thenReturn(Page.empty());

            assertTrue(rewardTransactionService.getMyRewardHistory(
                    filter("createdAt", "desc", 0, 10)).isEmpty());
        }

        // UTCID05 - Boundary: phân trang trang thứ 3, cỡ 20 -> truyền đúng xuống repository
        @Test
        void getMyRewardHistory_customPaging_passesPageAndSize() {
            User current = user(1L, 0, 0, null);
            when(userService.getCurrentUser()).thenReturn(current);
            when(rewardTransactionRepository.findByUser_UserId(eq(1L), any(Pageable.class)))
                    .thenReturn(Page.empty());

            rewardTransactionService.getMyRewardHistory(filter("createdAt", "desc", 2, 20));

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(rewardTransactionRepository).findByUser_UserId(eq(1L), captor.capture());
            assertEquals(2, captor.getValue().getPageNumber());
            assertEquals(20, captor.getValue().getPageSize());
        }

        // UTCID06 - Abnormal: filter = null -> chặn ngay đầu vào
        @Test
        void getMyRewardHistory_nullFilter_throwsEmptyFilter() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rewardTransactionService.getMyRewardHistory(null));

            assertEquals("Bộ lọc lịch sử điểm thưởng không được để trống", ex.getMessage());
            verifyNoInteractions(rewardTransactionRepository);
        }

        // UTCID07 - Abnormal: page = -1 -> số trang không hợp lệ
        @Test
        void getMyRewardHistory_negativePage_throwsInvalidPage() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rewardTransactionService.getMyRewardHistory(filter("createdAt", "desc", -1, 10)));

            assertEquals("Số trang không được nhỏ hơn 0", ex.getMessage());
            verifyNoInteractions(rewardTransactionRepository);
        }

        // UTCID08 - Boundary: size = 0 -> kích thước trang phải lớn hơn 0
        @Test
        void getMyRewardHistory_zeroSize_throwsInvalidSize() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rewardTransactionService.getMyRewardHistory(filter("createdAt", "desc", 0, 0)));

            assertEquals("Kích thước trang phải lớn hơn 0", ex.getMessage());
            verifyNoInteractions(rewardTransactionRepository);
        }

        // UTCID09 - Boundary: size = 101 -> vượt trần 100 bản ghi mỗi trang
        @Test
        void getMyRewardHistory_sizeOverLimit_throwsSizeExceeded() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rewardTransactionService.getMyRewardHistory(filter("createdAt", "desc", 0, 101)));

            assertEquals("Kích thước trang không được vượt quá 100", ex.getMessage());
            verifyNoInteractions(rewardTransactionRepository);
        }

        // UTCID10 - Abnormal: sortBy rỗng -> trường sắp xếp không được để trống
        @Test
        void getMyRewardHistory_blankSortBy_throwsEmptySortField() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rewardTransactionService.getMyRewardHistory(filter("  ", "desc", 0, 10)));

            assertEquals("Trường sắp xếp không được để trống", ex.getMessage());
            verifyNoInteractions(rewardTransactionRepository);
        }
    }
}
