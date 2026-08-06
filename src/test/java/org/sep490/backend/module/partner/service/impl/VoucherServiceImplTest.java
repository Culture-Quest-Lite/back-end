package org.sep490.backend.module.partner.service.impl;

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
import org.sep490.backend.module.content.service.inter.ImageService;
import org.sep490.backend.module.gamification.entity.RewardTransaction;
import org.sep490.backend.module.gamification.entity.enumeration.TransactionType;
import org.sep490.backend.module.gamification.repository.RewardTransactionRepository;
import org.sep490.backend.module.partner.dto.request.VoucherRequest;
import org.sep490.backend.module.partner.dto.response.VoucherResponse;
import org.sep490.backend.module.partner.dto.response.VoucherUsageResponse;
import org.sep490.backend.module.partner.entity.Voucher;
import org.sep490.backend.module.partner.entity.VoucherUsage;
import org.sep490.backend.module.partner.entity.enumeration.VoucherStatus;
import org.sep490.backend.module.partner.mapper.VoucherMapper;
import org.sep490.backend.module.partner.mapper.VoucherUsageMapper;
import org.sep490.backend.module.partner.repository.VoucherRepository;
import org.sep490.backend.module.partner.repository.VoucherUsageRepository;
import org.sep490.backend.module.user.service.UserService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho nghiệp vụ VOUCHER (đối tác + đổi điểm).
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoucherServiceImplTest {

    @Mock private VoucherRepository voucherRepository;
    @Mock private VoucherMapper voucherMapper;
    @Mock private UserService userService;
    @Mock private VoucherUsageRepository voucherUsageRepository;
    @Mock private VoucherUsageMapper voucherUsageMapper;
    @Mock private RewardTransactionRepository rewardTransactionRepository;
    @Mock private ImageService imageService;
    @Mock private UserRepository userRepository;

    @InjectMocks private VoucherServiceImpl voucherService;

    private static final LocalDateTime NOW = LocalDateTime.now();

    /** Voucher mẫu: còn 10 suất, cần 500 điểm, hiệu lực từ hôm qua đến 30 ngày sau. */
    private static Voucher voucher(VoucherStatus status, long quantityRemaining, long pointsRequired) {
        return Voucher.builder()
                .voucherId(1L)
                .voucherCode("ABC12345")
                .voucherName("Giảm 20% đồ uống")
                .description("Áp dụng tại tất cả chi nhánh")
                .pointsRequired(pointsRequired)
                .quantityTotal(100L)
                .quantityRemaining(quantityRemaining)
                .status(status)
                .startDate(NOW.minusDays(1))
                .endDate(NOW.plusDays(30))
                .build();
    }

    private static User user(long userId, int totalPoints) {
        User user = new User();
        user.setUserId(userId);
        user.setTotalPoints(totalPoints);
        user.setTotalXp(1200);
        return user;
    }

    // =====================================================================
    // Function: redeemVoucher
    // =====================================================================
    @Nested
    @DisplayName("redeemVoucher")
    class RedeemVoucherTest {

        // UTCID01 - Abnormal: voucher không tồn tại
        @Test
        void redeemVoucher_voucherNotFound_throwsNotFound() {
            when(voucherRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.redeemVoucher(1L));

            assertEquals("Voucher không tồn tại", ex.getMessage());
            verify(voucherUsageRepository, never()).save(any());
        }

        // UTCID02 - Abnormal: voucher đã bị xóa mềm
        @Test
        void redeemVoucher_deletedVoucher_throwsUnavailable() {
            when(voucherRepository.findById(1L))
                    .thenReturn(Optional.of(voucher(VoucherStatus.DELETED, 10L, 500L)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.redeemVoucher(1L));

            assertEquals("Voucher này hiện tại không khả dụng để đổi", ex.getMessage());
            verify(userRepository, never()).deductPoints(anyLong(), anyInt());
        }

        // UTCID03 - Boundary: hết sạch số lượng (quantityRemaining = 0)
        @Test
        void redeemVoucher_zeroQuantityRemaining_throwsUnavailable() {
            when(voucherRepository.findById(1L))
                    .thenReturn(Optional.of(voucher(VoucherStatus.ACTIVE, 0L, 500L)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.redeemVoucher(1L));

            assertEquals("Voucher này hiện tại không khả dụng để đổi", ex.getMessage());
            verify(userRepository, never()).deductPoints(anyLong(), anyInt());
        }

        // UTCID04 - Abnormal: voucher đã quá hạn (endDate ở quá khứ)
        @Test
        void redeemVoucher_expiredVoucher_throwsUnavailable() {
            Voucher expired = voucher(VoucherStatus.ACTIVE, 10L, 500L);
            expired.setStartDate(NOW.minusDays(60));
            expired.setEndDate(NOW.minusDays(1));
            when(voucherRepository.findById(1L)).thenReturn(Optional.of(expired));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.redeemVoucher(1L));

            assertEquals("Voucher này hiện tại không khả dụng để đổi", ex.getMessage());
        }

        // UTCID05 - Abnormal: voucher chưa tới ngày bắt đầu
        @Test
        void redeemVoucher_notYetStarted_throwsUnavailable() {
            Voucher future = voucher(VoucherStatus.ACTIVE, 10L, 500L);
            future.setStartDate(NOW.plusDays(1));
            future.setEndDate(NOW.plusDays(30));
            when(voucherRepository.findById(1L)).thenReturn(Optional.of(future));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.redeemVoucher(1L));

            assertEquals("Voucher này hiện tại không khả dụng để đổi", ex.getMessage());
        }

        // UTCID06 - Abnormal: không đủ điểm tích lũy (có 499, cần 500)
        @Test
        void redeemVoucher_notEnoughPoints_throwsInsufficientPoints() {
            when(voucherRepository.findById(1L))
                    .thenReturn(Optional.of(voucher(VoucherStatus.ACTIVE, 10L, 500L)));
            when(userService.getCurrentUser()).thenReturn(user(1L, 499));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.redeemVoucher(1L));

            assertEquals("Bạn không đủ số điểm tích lũy để đổi voucher này", ex.getMessage());
            verify(userRepository, never()).deductPoints(anyLong(), anyInt());
        }

        // UTCID07 - Abnormal: đã đổi voucher này trước đó
        @Test
        void redeemVoucher_alreadyRedeemed_throwsDuplicate() {
            when(voucherRepository.findById(1L))
                    .thenReturn(Optional.of(voucher(VoucherStatus.ACTIVE, 10L, 500L)));
            when(userService.getCurrentUser()).thenReturn(user(1L, 1000));
            when(voucherUsageRepository.existsByUserUserIdAndVoucherVoucherId(1L, 1L)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.redeemVoucher(1L));

            assertEquals("Bạn đã đổi voucher này trước đó", ex.getMessage());
            verify(userRepository, never()).deductPoints(anyLong(), anyInt());
        }

        // UTCID08 - Abnormal: trừ điểm thất bại do race condition (số dư vừa thay đổi)
        @Test
        void redeemVoucher_deductPointsRaceCondition_throwsTransactionFailed() {
            when(voucherRepository.findById(1L))
                    .thenReturn(Optional.of(voucher(VoucherStatus.ACTIVE, 10L, 500L)));
            when(userService.getCurrentUser()).thenReturn(user(1L, 1000));
            when(voucherUsageRepository.existsByUserUserIdAndVoucherVoucherId(anyLong(), anyLong()))
                    .thenReturn(false);
            when(userRepository.deductPoints(1L, 500)).thenReturn(0);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.redeemVoucher(1L));

            assertEquals("Giao dịch thất bại do số dư điểm thay đổi. Vui lòng thử lại!", ex.getMessage());
            verify(voucherRepository, never()).decrementQuantityRemaining(anyLong());
        }

        // UTCID09 - Abnormal: voucher vừa hết số lượng do người khác đổi trước (race condition)
        @Test
        void redeemVoucher_quantityRaceCondition_throwsSoldOut() {
            when(voucherRepository.findById(1L))
                    .thenReturn(Optional.of(voucher(VoucherStatus.ACTIVE, 1L, 500L)));
            when(userService.getCurrentUser()).thenReturn(user(1L, 1000));
            when(voucherUsageRepository.existsByUserUserIdAndVoucherVoucherId(anyLong(), anyLong()))
                    .thenReturn(false);
            when(userRepository.deductPoints(anyLong(), anyInt())).thenReturn(1);
            when(voucherRepository.decrementQuantityRemaining(1L)).thenReturn(0);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.redeemVoucher(1L));

            assertEquals("Rất tiếc, voucher vừa mới hết số lượng!", ex.getMessage());
            verify(voucherUsageRepository, never()).save(any());
        }

        // UTCID10 - Boundary: điểm đúng bằng yêu cầu (1000 điểm / cần 1000) -> đổi được, còn 0
        @Test
        void redeemVoucher_pointsExactlyEqualRequired_succeedsWithZeroBalance() {
            when(voucherRepository.findById(1L))
                    .thenReturn(Optional.of(voucher(VoucherStatus.ACTIVE, 10L, 1000L)));
            User currentUser = user(1L, 1000);
            when(userService.getCurrentUser()).thenReturn(currentUser);
            when(voucherUsageRepository.existsByUserUserIdAndVoucherVoucherId(anyLong(), anyLong()))
                    .thenReturn(false);
            when(userRepository.deductPoints(anyLong(), anyInt())).thenReturn(1);
            when(voucherRepository.decrementQuantityRemaining(anyLong())).thenReturn(1);
            when(voucherUsageRepository.save(any(VoucherUsage.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            voucherService.redeemVoucher(1L);

            assertEquals(0, currentUser.getTotalPoints());
            ArgumentCaptor<RewardTransaction> captor = ArgumentCaptor.forClass(RewardTransaction.class);
            verify(rewardTransactionRepository).save(captor.capture());
            assertEquals(0L, captor.getValue().getPointsBalance());
        }

        // UTCID11 - Normal: đổi voucher thành công, ghi lịch sử điểm âm
        @Test
        void redeemVoucher_validRequest_createsUsageAndRewardTransaction() {
            Voucher target = voucher(VoucherStatus.ACTIVE, 10L, 500L);
            User currentUser = user(1L, 1000);
            when(voucherRepository.findById(1L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(currentUser);
            when(voucherUsageRepository.existsByUserUserIdAndVoucherVoucherId(anyLong(), anyLong()))
                    .thenReturn(false);
            when(userRepository.deductPoints(1L, 500)).thenReturn(1);
            when(voucherRepository.decrementQuantityRemaining(1L)).thenReturn(1);
            when(voucherUsageRepository.save(any(VoucherUsage.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            VoucherUsageResponse expected = mock(VoucherUsageResponse.class);
            when(voucherUsageMapper.toResponse(any(VoucherUsage.class))).thenReturn(expected);

            VoucherUsageResponse actual = voucherService.redeemVoucher(1L);

            assertSame(expected, actual);
            assertEquals(500, currentUser.getTotalPoints());

            ArgumentCaptor<VoucherUsage> usageCaptor = ArgumentCaptor.forClass(VoucherUsage.class);
            verify(voucherUsageRepository).save(usageCaptor.capture());
            VoucherUsage usage = usageCaptor.getValue();
            assertEquals("ABC12345", usage.getVoucherCode());
            assertEquals(500L, usage.getPointsRequired());
            assertFalse(usage.getIsUsed());
            assertEquals(target.getEndDate(), usage.getExpiredAt());

            ArgumentCaptor<RewardTransaction> txCaptor = ArgumentCaptor.forClass(RewardTransaction.class);
            verify(rewardTransactionRepository).save(txCaptor.capture());
            RewardTransaction tx = txCaptor.getValue();
            assertEquals(-500L, tx.getPointsAmount());
            assertEquals(0L, tx.getXpAmount());
            assertEquals(500L, tx.getPointsBalance());
            assertEquals(TransactionType.REDEEM_VOUCHER, tx.getTransactionType());
        }
    }

    // =====================================================================
    // Function: useVoucher
    // =====================================================================
    @Nested
    @DisplayName("useVoucher")
    class UseVoucherTest {

        private static VoucherUsage usage(Boolean isUsed, LocalDateTime expiredAt) {
            return VoucherUsage.builder()
                    .voucherUsageId(9L)
                    .voucherCode("ABC12345")
                    .pointsRequired(500L)
                    .isUsed(isUsed)
                    .redeemedAt(NOW.minusDays(2))
                    .expiredAt(expiredAt)
                    .build();
        }

        // UTCID01 - Abnormal: mã voucher không tồn tại
        @Test
        void useVoucher_codeNotFound_throwsInvalidCode() {
            when(voucherUsageRepository.findByVoucherCode("SAI-MA")).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.useVoucher("SAI-MA"));

            assertEquals("Mã voucher không tồn tại hoặc không hợp lệ", ex.getMessage());
            verify(voucherUsageRepository, never()).save(any());
        }

        // UTCID02 - Abnormal: voucher đã dùng rồi -> chống dùng lại
        @Test
        void useVoucher_alreadyUsed_throwsAlreadyUsed() {
            when(voucherUsageRepository.findByVoucherCode("ABC12345"))
                    .thenReturn(Optional.of(usage(true, NOW.plusDays(10))));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.useVoucher("ABC12345"));

            assertEquals("Voucher này đã được sử dụng trước đó", ex.getMessage());
            verify(voucherUsageRepository, never()).save(any());
        }

        // UTCID03 - Abnormal: voucher đã hết hạn sử dụng
        @Test
        void useVoucher_expired_throwsExpired() {
            when(voucherUsageRepository.findByVoucherCode("ABC12345"))
                    .thenReturn(Optional.of(usage(false, NOW.minusDays(1))));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.useVoucher("ABC12345"));

            assertEquals("Voucher này đã hết hạn sử dụng", ex.getMessage());
            verify(voucherUsageRepository, never()).save(any());
        }

        // UTCID04 - Boundary: expiredAt = null (không giới hạn hạn dùng) -> vẫn dùng được
        @Test
        void useVoucher_nullExpiry_succeeds() {
            VoucherUsage target = usage(false, null);
            when(voucherUsageRepository.findByVoucherCode("ABC12345")).thenReturn(Optional.of(target));
            when(voucherUsageRepository.save(any(VoucherUsage.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            voucherService.useVoucher("ABC12345");

            assertTrue(target.getIsUsed());
            assertNotNull(target.getUsedAt());
        }

        // UTCID05 - Normal: dùng voucher hợp lệ -> đánh dấu đã dùng và ghi thời điểm
        @Test
        void useVoucher_validCode_marksUsedAndSetsUsedAt() {
            VoucherUsage target = usage(false, NOW.plusDays(10));
            when(voucherUsageRepository.findByVoucherCode("ABC12345")).thenReturn(Optional.of(target));
            when(voucherUsageRepository.save(any(VoucherUsage.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            VoucherUsageResponse expected = mock(VoucherUsageResponse.class);
            when(voucherUsageMapper.toResponse(target)).thenReturn(expected);

            assertSame(expected, voucherService.useVoucher("ABC12345"));
            assertTrue(target.getIsUsed());
            assertNotNull(target.getUsedAt());
        }
    }

    // =====================================================================
    // Function: updateVoucher
    // =====================================================================
    @Nested
    @DisplayName("updateVoucher")
    class UpdateVoucherTest {

        private static VoucherRequest updateRequest(String code, LocalDateTime start, LocalDateTime end) {
            VoucherRequest request = new VoucherRequest();
            request.setVoucherCode(code);
            request.setVoucherName("Giảm 30% đồ uống");
            request.setStartDate(start);
            request.setEndDate(end);
            return request;
        }

        // UTCID01 - Abnormal: voucher không tồn tại
        @Test
        void updateVoucher_notFound_throwsNotFound() {
            when(voucherRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.updateVoucher(1L,
                            updateRequest("ABC12345", NOW, NOW.plusDays(10))));

            assertEquals("Voucher không tồn tại", ex.getMessage());
            verify(voucherRepository, never()).save(any());
        }

        // UTCID02 - Abnormal: voucher đã bị xóa mềm -> coi như không tồn tại
        @Test
        void updateVoucher_deletedVoucher_throwsNotFound() {
            when(voucherRepository.findById(1L))
                    .thenReturn(Optional.of(voucher(VoucherStatus.DELETED, 10L, 500L)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.updateVoucher(1L,
                            updateRequest("ABC12345", NOW, NOW.plusDays(10))));

            assertEquals("Voucher không tồn tại", ex.getMessage());
            verify(voucherRepository, never()).save(any());
        }

        // UTCID03 - Abnormal: cố đổi mã voucher -> bị chặn
        @Test
        void updateVoucher_changingVoucherCode_throwsCodeImmutable() {
            when(voucherRepository.findById(1L))
                    .thenReturn(Optional.of(voucher(VoucherStatus.ACTIVE, 10L, 500L)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.updateVoucher(1L,
                            updateRequest("MA-KHAC", NOW, NOW.plusDays(10))));

            assertEquals("Không được phép thay đổi mã voucher", ex.getMessage());
            verify(voucherRepository, never()).save(any());
        }

        // UTCID04 - Abnormal: ngày bắt đầu sau ngày kết thúc
        @Test
        void updateVoucher_startAfterEnd_throwsInvalidDateRange() {
            when(voucherRepository.findById(1L))
                    .thenReturn(Optional.of(voucher(VoucherStatus.ACTIVE, 10L, 500L)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.updateVoucher(1L,
                            updateRequest("ABC12345", NOW.plusDays(10), NOW)));

            assertEquals("Ngày bắt đầu phải trước ngày kết thúc", ex.getMessage());
            verify(voucherRepository, never()).save(any());
        }

        // UTCID05 - Normal: request không truyền status -> giữ nguyên status cũ
        @Test
        void updateVoucher_nullStatusInRequest_keepsOldStatus() {
            Voucher target = voucher(VoucherStatus.ACTIVE, 10L, 500L);
            when(voucherRepository.findById(1L)).thenReturn(Optional.of(target));
            when(voucherRepository.save(any(Voucher.class))).thenAnswer(inv -> inv.getArgument(0));

            VoucherRequest request = updateRequest("ABC12345", NOW, NOW.plusDays(10));
            request.setStatus(null);

            voucherService.updateVoucher(1L, request);

            assertEquals(VoucherStatus.ACTIVE, target.getStatus());
            verify(voucherRepository).save(target);
        }

        // UTCID06 - Normal: cập nhật hợp lệ -> gọi mapper và lưu, ảnh được resolve từ ảnh cũ
        @Test
        void updateVoucher_validRequest_updatesAndResolvesImage() {
            Voucher target = voucher(VoucherStatus.ACTIVE, 10L, 500L);
            target.setImageUrl("https://s3/vouchers/cu.png");
            when(voucherRepository.findById(1L)).thenReturn(Optional.of(target));
            when(voucherRepository.save(any(Voucher.class))).thenAnswer(inv -> inv.getArgument(0));
            when(imageService.resolveImageUrl(eq("https://s3/vouchers/cu.png"), any(), eq("vouchers")))
                    .thenReturn("https://s3/vouchers/moi.png");

            VoucherResponse expected = mock(VoucherResponse.class);
            when(voucherMapper.toResponse(target)).thenReturn(expected);

            VoucherRequest request = updateRequest("ABC12345", NOW, NOW.plusDays(10));
            assertSame(expected, voucherService.updateVoucher(1L, request));

            verify(voucherMapper).updateEntityFromRequest(request, target);
            assertEquals("https://s3/vouchers/moi.png", target.getImageUrl());
        }
    }

    // =====================================================================
    // Function: createVoucher
    // =====================================================================
    @Nested
    @DisplayName("createVoucher")
    class CreateVoucherTest {

        private static VoucherRequest createRequest(String code, LocalDateTime start, LocalDateTime end) {
            VoucherRequest request = new VoucherRequest();
            request.setVoucherCode(code);
            request.setVoucherName("Giảm 20% đồ uống");
            request.setStartDate(start);
            request.setEndDate(end);
            return request;
        }

        // UTCID01 - Abnormal: ngày bắt đầu sau ngày kết thúc
        @Test
        void createVoucher_startAfterEnd_throwsInvalidDateRange() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.createVoucher(
                            createRequest("ABC12345", NOW.plusDays(10), NOW)));

            assertEquals("Ngày bắt đầu phải trước ngày kết thúc", ex.getMessage());
            verify(voucherRepository, never()).save(any());
        }

        // UTCID02 - Abnormal: mã voucher đã tồn tại
        @Test
        void createVoucher_duplicateCode_throwsCodeExists() {
            when(voucherRepository.existsByVoucherCode("ABC12345")).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.createVoucher(
                            createRequest("ABC12345", NOW, NOW.plusDays(10))));

            assertEquals("Mã voucher đã tồn tại", ex.getMessage());
            verify(voucherRepository, never()).save(any());
        }

        // UTCID03 - Normal: tạo thành công -> status PENDING, sinh mã ngẫu nhiên 8 ký tự
        @Test
        void createVoucher_validRequest_createsPendingVoucherWithGeneratedCode() {
            Voucher mapped = new Voucher();
            User partner = user(7L, 0);
            when(voucherRepository.existsByVoucherCode(anyString())).thenReturn(false);
            when(userService.getCurrentUser()).thenReturn(partner);
            when(voucherMapper.toEntity(any(VoucherRequest.class))).thenReturn(mapped);
            when(voucherRepository.save(any(Voucher.class))).thenAnswer(inv -> inv.getArgument(0));
            when(imageService.resolveImageUrl(isNull(), any(), eq("vouchers")))
                    .thenReturn("https://s3/vouchers/moi.png");

            VoucherResponse expected = mock(VoucherResponse.class);
            when(voucherMapper.toResponse(mapped)).thenReturn(expected);

            assertSame(expected, voucherService.createVoucher(
                    createRequest("ABC12345", NOW, NOW.plusDays(10))));

            assertEquals(VoucherStatus.PENDING, mapped.getStatus());
            assertSame(partner, mapped.getPartner());
            assertEquals(8, mapped.getVoucherCode().length());
            assertEquals("https://s3/vouchers/moi.png", mapped.getImageUrl());
        }
    }

    // =====================================================================
    // Function: deleteVoucher / getVoucherById
    // =====================================================================
    @Nested
    @DisplayName("deleteVoucher")
    class DeleteVoucherTest {

        // UTCID01 - Abnormal: voucher không tồn tại
        @Test
        void deleteVoucher_notFound_throwsNotFound() {
            when(voucherRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.deleteVoucher(1L));

            assertEquals("Voucher không tồn tại", ex.getMessage());
        }

        // UTCID02 - Abnormal: voucher đã bị xóa trước đó
        @Test
        void deleteVoucher_alreadyDeleted_throwsAlreadyDeleted() {
            when(voucherRepository.findById(1L))
                    .thenReturn(Optional.of(voucher(VoucherStatus.DELETED, 10L, 500L)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> voucherService.deleteVoucher(1L));

            assertEquals("Voucher đã bị xóa trước đó", ex.getMessage());
            verify(voucherRepository, never()).save(any());
        }

        // UTCID03 - Normal: xóa mềm -> chuyển status sang DELETED
        @Test
        void deleteVoucher_valid_setsStatusDeleted() {
            Voucher target = voucher(VoucherStatus.ACTIVE, 10L, 500L);
            when(voucherRepository.findById(1L)).thenReturn(Optional.of(target));

            voucherService.deleteVoucher(1L);

            assertEquals(VoucherStatus.DELETED, target.getStatus());
            verify(voucherRepository).save(target);
        }
    }
}
