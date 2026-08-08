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
import org.sep490.backend.common.service.PayOsInvoicePaymentService;
import org.sep490.backend.module.admin.dto.response.PaymentInitResponse;
import org.sep490.backend.module.admin.entity.Invoice;
import org.sep490.backend.module.admin.entity.SubscriptionPlan;
import org.sep490.backend.module.admin.entity.enumeration.BillingCycleEnum;
import org.sep490.backend.module.admin.entity.enumeration.InvoicePaymentStatus;
import org.sep490.backend.module.admin.entity.enumeration.InvoiceStatus;
import org.sep490.backend.module.admin.entity.enumeration.PlanType;
import org.sep490.backend.module.admin.entity.enumeration.SubscriptionPlanStatus;
import org.sep490.backend.module.admin.repository.InvoiceRepository;
import org.sep490.backend.module.admin.repository.SubscriptionPlanRepository;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.user.dto.request.PremiumSubscribeRequest;
import org.sep490.backend.module.user.dto.response.PremiumSubscriptionResponse;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.sep490.backend.module.user.service.UserService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho MUA GÓI PREMIUM của người dùng.
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PremiumSubscriptionServiceImplTest {

    @Mock private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private UserService userService;
    @Mock private PayOsInvoicePaymentService payOsInvoicePaymentService;

    @InjectMocks private PremiumSubscriptionServiceImpl premiumSubscriptionService;

    private static User user(Long userId, UserRole role) {
        User user = new User();
        user.setUserId(userId);
        user.setRole(role);
        return user;
    }

    /** Gói Premium 99.000đ/tháng, 990.000đ/năm. */
    private static SubscriptionPlan plan(PlanType type, SubscriptionPlanStatus status,
                                         Long monthly, Long yearly) {
        return SubscriptionPlan.builder()
                .subscriptionPlanId(1L)
                .subscriptionPlanName("Premium 1 tháng")
                .priceMonthly(monthly)
                .priceYearly(yearly)
                .planType(type)
                .status(status)
                .build();
    }

    private static PremiumSubscribeRequest request(BillingCycleEnum cycle) {
        PremiumSubscribeRequest request = new PremiumSubscribeRequest();
        request.setSubscriptionPlanId(1L);
        request.setBillingCycle(cycle);
        request.setRedirectUrl("https://app/premium/return");
        return request;
    }

    // =====================================================================
    // Function: subscribe
    // =====================================================================
    @Nested
    @DisplayName("subscribe")
    class SubscribeTest {

        // UTCID01 - Abnormal: tài khoản CURATOR không được mua gói Premium
        @Test
        void subscribe_nonExplorerRole_throwsExplorerOnly() {
            when(userService.getCurrentUser()).thenReturn(user(1L, UserRole.CURATOR));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> premiumSubscriptionService.subscribe(request(BillingCycleEnum.MONTHLY)));

            assertEquals("Chỉ người dùng Explorer mới có thể mua gói Premium", ex.getMessage());
            verify(invoiceRepository, never()).save(any());
        }

        // UTCID02 - Abnormal: gói đăng ký không tồn tại
        @Test
        void subscribe_planNotFound_throwsPlanNotFound() {
            when(userService.getCurrentUser()).thenReturn(user(1L, UserRole.EXPLORER));
            when(subscriptionPlanRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> premiumSubscriptionService.subscribe(request(BillingCycleEnum.MONTHLY)));

            assertEquals("Gói đăng ký không tồn tại", ex.getMessage());
        }

        // UTCID03 - Abnormal: chọn nhầm gói PARTNER
        @Test
        void subscribe_partnerPlan_throwsWrongPlanType() {
            when(userService.getCurrentUser()).thenReturn(user(1L, UserRole.EXPLORER));
            when(subscriptionPlanRepository.findById(1L)).thenReturn(Optional.of(
                    plan(PlanType.PARTNER, SubscriptionPlanStatus.ACTIVE, 99000L, 990000L)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> premiumSubscriptionService.subscribe(request(BillingCycleEnum.MONTHLY)));

            assertEquals("Gói này không dành cho người dùng Premium", ex.getMessage());
        }

        // UTCID04 - Abnormal: gói Premium đã ngừng bán (INACTIVE)
        @Test
        void subscribe_inactivePlan_throwsWrongPlanType() {
            when(userService.getCurrentUser()).thenReturn(user(1L, UserRole.EXPLORER));
            when(subscriptionPlanRepository.findById(1L)).thenReturn(Optional.of(
                    plan(PlanType.PREMIUM, SubscriptionPlanStatus.INACTIVE, 99000L, 990000L)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> premiumSubscriptionService.subscribe(request(BillingCycleEnum.MONTHLY)));

            assertEquals("Gói này không dành cho người dùng Premium", ex.getMessage());
        }

        // UTCID05 - Abnormal: chưa cấu hình giá theo tháng (null)
        @Test
        void subscribe_nullMonthlyPrice_throwsInvalidPrice() {
            when(userService.getCurrentUser()).thenReturn(user(1L, UserRole.EXPLORER));
            when(subscriptionPlanRepository.findById(1L)).thenReturn(Optional.of(
                    plan(PlanType.PREMIUM, SubscriptionPlanStatus.ACTIVE, null, 990000L)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> premiumSubscriptionService.subscribe(request(BillingCycleEnum.MONTHLY)));

            assertEquals("Giá gói không hợp lệ", ex.getMessage());
        }

        // UTCID06 - Boundary: giá gói = 0 -> không hợp lệ
        @Test
        void subscribe_zeroPrice_throwsInvalidPrice() {
            when(userService.getCurrentUser()).thenReturn(user(1L, UserRole.EXPLORER));
            when(subscriptionPlanRepository.findById(1L)).thenReturn(Optional.of(
                    plan(PlanType.PREMIUM, SubscriptionPlanStatus.ACTIVE, 0L, 990000L)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> premiumSubscriptionService.subscribe(request(BillingCycleEnum.MONTHLY)));

            assertEquals("Giá gói không hợp lệ", ex.getMessage());
        }

        // UTCID07 - Normal: mua gói THÁNG -> hóa đơn PENDING 99.000đ, chuyển sang PayOS
        @Test
        void subscribe_monthlyValid_createsPendingInvoiceAndInitiatesPayment() {
            User explorer = user(1L, UserRole.EXPLORER);
            when(userService.getCurrentUser()).thenReturn(explorer);
            when(subscriptionPlanRepository.findById(1L)).thenReturn(Optional.of(
                    plan(PlanType.PREMIUM, SubscriptionPlanStatus.ACTIVE, 99000L, 990000L)));
            when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

            PaymentInitResponse expected = mock(PaymentInitResponse.class);
            when(payOsInvoicePaymentService.initiatePayOsPayment(
                    any(Invoice.class), eq("https://app/premium/return"))).thenReturn(expected);

            assertSame(expected,
                    premiumSubscriptionService.subscribe(request(BillingCycleEnum.MONTHLY)));

            ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
            verify(invoiceRepository).save(captor.capture());
            Invoice invoice = captor.getValue();
            assertSame(explorer, invoice.getUser());
            assertNull(invoice.getPartnerInfo());
            assertEquals(99000L, invoice.getPaidAmount());
            assertEquals(InvoiceStatus.PENDING, invoice.getStatus());
            assertEquals(InvoicePaymentStatus.PENDING, invoice.getPaymentStatus());
            assertTrue(invoice.getInvoiceCode().startsWith("INV"));
        }

        // UTCID08 - Normal: mua gói NĂM -> lấy giá theo năm 990.000đ
        @Test
        void subscribe_yearlyValid_usesYearlyPrice() {
            when(userService.getCurrentUser()).thenReturn(user(1L, UserRole.EXPLORER));
            when(subscriptionPlanRepository.findById(1L)).thenReturn(Optional.of(
                    plan(PlanType.PREMIUM, SubscriptionPlanStatus.ACTIVE, 99000L, 990000L)));
            when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

            premiumSubscriptionService.subscribe(request(BillingCycleEnum.YEARLY));

            ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
            verify(invoiceRepository).save(captor.capture());
            assertEquals(990000L, captor.getValue().getPaidAmount());
            assertEquals(BillingCycleEnum.YEARLY, captor.getValue().getBillingCycle());
        }
    }

    // =====================================================================
    // Function: confirmPayment
    // =====================================================================
    @Nested
    @DisplayName("confirmPayment")
    class ConfirmPaymentTest {

        private static Invoice premiumInvoice(InvoiceStatus status, InvoicePaymentStatus paymentStatus) {
            return Invoice.builder()
                    .invoiceId(10L)
                    .user(user(1L, UserRole.EXPLORER))
                    .subscriptionPlan(plan(PlanType.PREMIUM, SubscriptionPlanStatus.ACTIVE,
                            99000L, 990000L))
                    .billingCycle(BillingCycleEnum.MONTHLY)
                    .status(status)
                    .paymentStatus(paymentStatus)
                    .paidAmount(99000L)
                    .invoiceCode("INV123")
                    .build();
        }

        // UTCID01 - Abnormal: hóa đơn không tồn tại hoặc không thuộc người dùng hiện tại
        @Test
        void confirmPayment_invoiceNotFound_throwsNotFound() {
            when(userService.getCurrentUser()).thenReturn(user(1L, UserRole.EXPLORER));
            when(invoiceRepository.findPremiumInvoiceForUser(10L, 1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> premiumSubscriptionService.confirmPayment(10L));

            assertEquals("Hóa đơn không tồn tại", ex.getMessage());
            verify(payOsInvoicePaymentService, never()).reconcileInvoiceWithPayOs(any());
        }

        // UTCID02 - Normal: đối soát với PayOS rồi đọc lại trạng thái mới nhất
        @Test
        void confirmPayment_valid_reconcilesThenReturnsRefreshedInvoice() {
            Invoice before = premiumInvoice(InvoiceStatus.PENDING, InvoicePaymentStatus.PENDING);
            Invoice after = premiumInvoice(InvoiceStatus.ACTIVE, InvoicePaymentStatus.PAID);
            when(userService.getCurrentUser()).thenReturn(user(1L, UserRole.EXPLORER));
            when(invoiceRepository.findPremiumInvoiceForUser(10L, 1L))
                    .thenReturn(Optional.of(before), Optional.of(after));

            PremiumSubscriptionResponse result = premiumSubscriptionService.confirmPayment(10L);

            verify(payOsInvoicePaymentService).reconcileInvoiceWithPayOs(before);
            assertEquals(InvoiceStatus.ACTIVE, result.getStatus());
            assertEquals(InvoicePaymentStatus.PAID, result.getPaymentStatus());
        }

        // UTCID03 - Boundary: lần đọc lại không thấy -> fallback về bản ghi ban đầu
        @Test
        void confirmPayment_refreshMisses_fallsBackToOriginalInvoice() {
            Invoice before = premiumInvoice(InvoiceStatus.PENDING, InvoicePaymentStatus.PENDING);
            when(userService.getCurrentUser()).thenReturn(user(1L, UserRole.EXPLORER));
            when(invoiceRepository.findPremiumInvoiceForUser(10L, 1L))
                    .thenReturn(Optional.of(before), Optional.empty());

            PremiumSubscriptionResponse result = premiumSubscriptionService.confirmPayment(10L);

            assertEquals(10L, result.getInvoiceId());
            assertEquals(InvoiceStatus.PENDING, result.getStatus());
        }
    }

    // =====================================================================
    // Function: getMyPremiumSubscription
    // =====================================================================
    @Nested
    @DisplayName("getMyPremiumSubscription")
    class GetMyPremiumSubscriptionTest {

        // UTCID01 - Normal: có lịch sử mua gói -> map đầy đủ thông tin gói
        @Test
        void getMyPremiumSubscription_hasInvoices_mapsPlanName() {
            Invoice invoice = Invoice.builder()
                    .invoiceId(10L)
                    .subscriptionPlan(plan(PlanType.PREMIUM, SubscriptionPlanStatus.ACTIVE,
                            99000L, 990000L))
                    .billingCycle(BillingCycleEnum.MONTHLY)
                    .status(InvoiceStatus.ACTIVE)
                    .paymentStatus(InvoicePaymentStatus.PAID)
                    .paidAmount(99000L)
                    .build();
            when(userService.getCurrentUser()).thenReturn(user(1L, UserRole.EXPLORER));
            when(invoiceRepository.findByUser_UserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(List.of(invoice));

            List<PremiumSubscriptionResponse> result =
                    premiumSubscriptionService.getMyPremiumSubscription();

            assertEquals(1, result.size());
            assertEquals("Premium 1 tháng", result.get(0).getPlanName());
            assertEquals(99000L, result.get(0).getPaidAmount());
        }

        // UTCID02 - Boundary: chưa từng mua gói nào -> danh sách rỗng
        @Test
        void getMyPremiumSubscription_noInvoices_returnsEmptyList() {
            when(userService.getCurrentUser()).thenReturn(user(1L, UserRole.EXPLORER));
            when(invoiceRepository.findByUser_UserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

            assertTrue(premiumSubscriptionService.getMyPremiumSubscription().isEmpty());
        }
    }
}
