package org.sep490.backend.common.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.module.admin.entity.Invoice;
import org.sep490.backend.module.admin.entity.SystemTransaction;
import org.sep490.backend.module.admin.entity.enumeration.BillingCycleEnum;
import org.sep490.backend.module.admin.entity.enumeration.InvoicePaymentStatus;
import org.sep490.backend.module.admin.entity.enumeration.InvoiceStatus;
import org.sep490.backend.module.admin.entity.enumeration.SystemTransactionStatus;
import org.sep490.backend.module.admin.repository.InvoiceRepository;
import org.sep490.backend.module.admin.repository.SystemTransactionRepository;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.authorization.service.EntitlementCacheService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho KÍCH HOẠT HÓA ĐƠN sau khi thanh toán (nâng cấp Premium, chống ghi nhận trùng).
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceActivationServiceImplTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private UserRepository userRepository;
    @Mock private SystemTransactionRepository systemTransactionRepository;
    @Mock private EntitlementCacheService entitlementCacheService;

    @InjectMocks private InvoiceActivationServiceImpl invoiceActivationService;

    private static User buyer(Long userId) {
        User user = new User();
        user.setUserId(userId);
        user.setIsPremium(false);
        return user;
    }

    private static Invoice invoice(Long invoiceId, User user, BillingCycleEnum cycle,
                                   InvoicePaymentStatus paymentStatus, Long orderCode) {
        return Invoice.builder()
                .invoiceId(invoiceId)
                .invoiceCode("INV-2026-0001")
                .user(user)
                .billingCycle(cycle)
                .paidAmount(199_000L)
                .status(InvoiceStatus.PENDING)
                .paymentStatus(paymentStatus)
                .payosOrderCode(orderCode)
                .build();
    }

    // =====================================================================
    // Function: markInvoicePaid
    // =====================================================================
    @Nested
    @DisplayName("markInvoicePaid")
    class MarkInvoicePaidTest {

        // UTCID01 - Abnormal: webhook PayOS bắn trùng, hóa đơn đã PAID -> bỏ qua, trả về false
        @Test
        void markInvoicePaid_alreadyPaid_returnsFalseAndSkips() {
            Invoice paid = invoice(1L, buyer(10L), BillingCycleEnum.MONTHLY,
                    InvoicePaymentStatus.PAID, 100000L);

            assertFalse(invoiceActivationService.markInvoicePaid(paid, "REF-DUPLICATE"));

            verify(invoiceRepository, never()).save(any());
            verify(userRepository, never()).save(any());
            verify(entitlementCacheService, never()).evict(anyLong());
        }

        // UTCID02 - Normal: user chưa từng mua Premium -> ACTIVE, hạn 1 tháng tính từ hiện tại
        @Test
        void markInvoicePaid_monthlyFirstPurchase_activatesPremiumForOneMonth() {
            User user = buyer(10L);
            Invoice target = invoice(1L, user, BillingCycleEnum.MONTHLY,
                    InvoicePaymentStatus.PENDING, 100000L);
            LocalDateTime before = LocalDateTime.now();

            when(invoiceRepository.findFirstByUser_UserIdAndStatusOrderByEndDateDesc(10L, InvoiceStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertTrue(invoiceActivationService.markInvoicePaid(target, "REF-PAYOS-001"));

            assertEquals(InvoicePaymentStatus.PAID, target.getPaymentStatus());
            assertEquals(InvoiceStatus.ACTIVE, target.getStatus());
            assertEquals("REF-PAYOS-001", target.getPayosTransactionId());
            assertTrue(user.getIsPremium());
            assertFalse(target.getStartDate().isBefore(before));
            assertEquals(target.getStartDate().plusMonths(1), target.getEndDate());
            verify(userRepository).save(user);
            verify(entitlementCacheService).evict(10L);
        }

        // UTCID03 - Normal: gói NĂM -> hạn cộng 1 năm thay vì 1 tháng
        @Test
        void markInvoicePaid_yearlyCycle_endDateIsOneYearLater() {
            User user = buyer(10L);
            Invoice target = invoice(2L, user, BillingCycleEnum.YEARLY,
                    InvoicePaymentStatus.PENDING, 200000L);
            when(invoiceRepository.findFirstByUser_UserIdAndStatusOrderByEndDateDesc(anyLong(), any()))
                    .thenReturn(Optional.empty());

            invoiceActivationService.markInvoicePaid(target, "REF-PAYOS-002");

            assertEquals(target.getStartDate().plusYears(1), target.getEndDate());
        }

        // UTCID04 - Boundary: gia hạn khi gói cũ CHƯA hết hạn -> nối tiếp từ ngày hết hạn cũ
        @Test
        void markInvoicePaid_renewBeforeExpiry_stacksOnTopOfCurrentEndDate() {
            User user = buyer(10L);
            LocalDateTime currentEnd = LocalDateTime.now().plusDays(12);
            Invoice running = Invoice.builder().invoiceId(9L).endDate(currentEnd).build();
            Invoice target = invoice(3L, user, BillingCycleEnum.MONTHLY,
                    InvoicePaymentStatus.PENDING, 300000L);

            when(invoiceRepository.findFirstByUser_UserIdAndStatusOrderByEndDateDesc(10L, InvoiceStatus.ACTIVE))
                    .thenReturn(Optional.of(running));

            invoiceActivationService.markInvoicePaid(target, "REF-PAYOS-003");

            assertEquals(currentEnd, target.getStartDate());
            assertEquals(currentEnd.plusMonths(1), target.getEndDate());
        }

        // UTCID05 - Boundary: gói cũ ĐÃ hết hạn -> tính lại từ hiện tại, không cộng dồn quá khứ
        @Test
        void markInvoicePaid_renewAfterExpiry_startsFromNow() {
            User user = buyer(10L);
            LocalDateTime expiredEnd = LocalDateTime.now().minusDays(5);
            Invoice expired = Invoice.builder().invoiceId(9L).endDate(expiredEnd).build();
            Invoice target = invoice(4L, user, BillingCycleEnum.MONTHLY,
                    InvoicePaymentStatus.PENDING, 400000L);

            when(invoiceRepository.findFirstByUser_UserIdAndStatusOrderByEndDateDesc(10L, InvoiceStatus.ACTIVE))
                    .thenReturn(Optional.of(expired));

            invoiceActivationService.markInvoicePaid(target, "REF-PAYOS-004");

            assertTrue(target.getStartDate().isAfter(expiredEnd));
            assertEquals(target.getStartDate().plusMonths(1), target.getEndDate());
        }

        // UTCID06 - Normal: hóa đơn của ĐỐI TÁC (user = null) -> giữ PENDING chờ admin duyệt
        @Test
        void markInvoicePaid_partnerInvoiceWithoutUser_staysPendingApproval() {
            Invoice target = invoice(5L, null, BillingCycleEnum.MONTHLY,
                    InvoicePaymentStatus.PENDING, 500000L);

            assertTrue(invoiceActivationService.markInvoicePaid(target, "REF-PAYOS-005"));

            assertEquals(InvoicePaymentStatus.PAID, target.getPaymentStatus());
            assertEquals(InvoiceStatus.PENDING, target.getStatus());
            verify(userRepository, never()).save(any());
            verify(entitlementCacheService, never()).evict(anyLong());
        }

        // UTCID07 - Normal: cập nhật giao dịch hệ thống sang SUCCESSED theo mã đơn PayOS
        @Test
        void markInvoicePaid_updatesSystemTransactionToSuccess() {
            Invoice target = invoice(6L, null, BillingCycleEnum.MONTHLY,
                    InvoicePaymentStatus.PENDING, 600000L);
            SystemTransaction transaction = SystemTransaction.builder()
                    .systemTransactionId(77L)
                    .status(SystemTransactionStatus.PENDING)
                    .build();
            when(systemTransactionRepository.findFirstByGatewayRefOrderByCreatedAtDesc("600000"))
                    .thenReturn(Optional.of(transaction));

            invoiceActivationService.markInvoicePaid(target, "REF-PAYOS-006");

            assertEquals(SystemTransactionStatus.SUCCESSED, transaction.getStatus());
            assertEquals("Thanh toán thành công qua PayOS. Ref: REF-PAYOS-006", transaction.getNotes());
            verify(systemTransactionRepository).save(transaction);
        }

        // UTCID08 - Boundary: chưa có mã đơn PayOS -> không tra cứu giao dịch, vẫn lưu hóa đơn
        @Test
        void markInvoicePaid_nullOrderCode_skipsTransactionLookup() {
            Invoice target = invoice(7L, null, BillingCycleEnum.MONTHLY,
                    InvoicePaymentStatus.PENDING, null);

            assertTrue(invoiceActivationService.markInvoicePaid(target, "REF-PAYOS-007"));

            verify(systemTransactionRepository, never()).findFirstByGatewayRefOrderByCreatedAtDesc(anyString());
            verify(invoiceRepository).save(target);
        }
    }

    // =====================================================================
    // Function: markInvoiceFailed
    // =====================================================================
    @Nested
    @DisplayName("markInvoiceFailed")
    class MarkInvoiceFailedTest {

        // UTCID01 - Abnormal: hóa đơn đã PAID -> KHÔNG được hạ về FAILED (bảo vệ tiền đã thu)
        @Test
        void markInvoiceFailed_alreadyPaid_doesNotDowngrade() {
            Invoice paid = invoice(1L, buyer(10L), BillingCycleEnum.MONTHLY,
                    InvoicePaymentStatus.PAID, 100000L);

            invoiceActivationService.markInvoiceFailed(paid);

            assertEquals(InvoicePaymentStatus.PAID, paid.getPaymentStatus());
            verify(invoiceRepository, never()).save(any());
        }

        // UTCID02 - Normal: hóa đơn PENDING -> chuyển FAILED và lưu lại
        @Test
        void markInvoiceFailed_pendingInvoice_marksFailed() {
            Invoice target = invoice(2L, buyer(10L), BillingCycleEnum.MONTHLY,
                    InvoicePaymentStatus.PENDING, 200000L);

            invoiceActivationService.markInvoiceFailed(target);

            assertEquals(InvoicePaymentStatus.FAILED, target.getPaymentStatus());
            verify(invoiceRepository).save(target);
        }

        // UTCID03 - Normal: cập nhật giao dịch hệ thống sang FAILED kèm ghi chú
        @Test
        void markInvoiceFailed_updatesSystemTransactionToFailed() {
            Invoice target = invoice(3L, buyer(10L), BillingCycleEnum.MONTHLY,
                    InvoicePaymentStatus.PENDING, 300000L);
            SystemTransaction transaction = SystemTransaction.builder()
                    .systemTransactionId(88L)
                    .status(SystemTransactionStatus.PENDING)
                    .build();
            when(systemTransactionRepository.findFirstByGatewayRefOrderByCreatedAtDesc("300000"))
                    .thenReturn(Optional.of(transaction));

            invoiceActivationService.markInvoiceFailed(target);

            assertEquals(SystemTransactionStatus.FAILED, transaction.getStatus());
            assertEquals("Thanh toán thất bại qua PayOS.", transaction.getNotes());
            verify(systemTransactionRepository).save(transaction);
        }

        // UTCID04 - Boundary: không tìm thấy giao dịch tương ứng -> không lỗi, chỉ lưu hóa đơn
        @Test
        void markInvoiceFailed_transactionNotFound_stillSavesInvoice() {
            Invoice target = invoice(4L, buyer(10L), BillingCycleEnum.MONTHLY,
                    InvoicePaymentStatus.PENDING, 400000L);
            when(systemTransactionRepository.findFirstByGatewayRefOrderByCreatedAtDesc("400000"))
                    .thenReturn(Optional.empty());

            assertDoesNotThrow(() -> invoiceActivationService.markInvoiceFailed(target));

            verify(invoiceRepository).save(target);
            verify(systemTransactionRepository, never()).save(any());
        }
    }
}
