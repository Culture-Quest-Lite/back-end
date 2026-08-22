package org.sep490.backend.common.service.impl;

import org.junit.jupiter.api.BeforeEach;
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
import org.sep490.backend.common.service.InvoiceActivationService;
import org.sep490.backend.config.payos.PayOsProperties;
import org.sep490.backend.module.admin.dto.response.PaymentInitResponse;
import org.sep490.backend.module.admin.entity.Invoice;
import org.sep490.backend.module.admin.entity.SystemTransaction;
import org.sep490.backend.module.admin.entity.enumeration.InvoicePaymentStatus;
import org.sep490.backend.module.admin.entity.enumeration.PaymentGateway;
import org.sep490.backend.module.admin.entity.enumeration.SystemTransactionStatus;
import org.sep490.backend.module.admin.entity.enumeration.SystemTransactionType;
import org.sep490.backend.module.admin.repository.InvoiceRepository;
import org.sep490.backend.module.admin.repository.SystemTransactionRepository;
import vn.payos.PayOS;
import vn.payos.exception.PayOSException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.v2.paymentRequests.Transaction;
import vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho TÍCH HỢP THANH TOÁN PAYOS (tạo link thanh toán và đối soát trạng thái).
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayOsInvoicePaymentServiceImplTest {

    @Mock private PayOS payOS;
    @Mock private PaymentRequestsService paymentRequestsService;
    @Mock private PayOsProperties payOsProperties;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private SystemTransactionRepository systemTransactionRepository;
    @Mock private InvoiceActivationService invoiceActivationService;

    @InjectMocks private PayOsInvoicePaymentServiceImpl payOsInvoicePaymentService;

    @BeforeEach
    void setUp() {
        when(payOS.paymentRequests()).thenReturn(paymentRequestsService);
        when(payOsProperties.getReturnUrl()).thenReturn("https://culturequest.vn/payment/success");
        when(payOsProperties.getCancelUrl()).thenReturn("https://culturequest.vn/payment/cancel");
    }

    private static Invoice invoice(Long invoiceId, Long orderCode, InvoicePaymentStatus paymentStatus) {
        return Invoice.builder()
                .invoiceId(invoiceId)
                .invoiceCode("INV-2026-0001")
                .paidAmount(199_000L)
                .paymentStatus(paymentStatus)
                .payosOrderCode(orderCode)
                .build();
    }

    private static CreatePaymentLinkResponse payOsLink() {
        CreatePaymentLinkResponse response = new CreatePaymentLinkResponse();
        response.setPaymentLinkId("f1a2b3c4d5");
        response.setCheckoutUrl("https://pay.payos.vn/web/f1a2b3c4d5");
        response.setQrCode("00020101021238570010A000000727");
        return response;
    }

    private static PaymentLink paymentLink(PaymentLinkStatus status, String reference) {
        PaymentLink link = new PaymentLink();
        link.setStatus(status);
        if (reference != null) {
            Transaction transaction = new Transaction();
            transaction.setReference(reference);
            link.setTransactions(List.of(transaction));
        }
        return link;
    }

    // =====================================================================
    // Function: initiatePayOsPayment
    // =====================================================================
    @Nested
    @DisplayName("initiatePayOsPayment")
    class InitiatePayOsPaymentTest {

        // UTCID01 - Abnormal: hóa đơn chưa lưu DB (invoiceId = null) -> không tạo được mã đơn
        @Test
        void initiatePayOsPayment_invoiceNotPersisted_throwsMustSaveFirst() {
            Invoice unsaved = invoice(null, null, InvoicePaymentStatus.PENDING);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> payOsInvoicePaymentService.initiatePayOsPayment(unsaved, null));

            assertEquals("Hóa đơn phải được lưu trước khi tạo link thanh toán", ex.getMessage());
            verify(invoiceRepository, never()).save(any());
        }

        // UTCID02 - Normal: không truyền redirectUrl -> dùng URL mặc định trong cấu hình
        @Test
        void initiatePayOsPayment_nullRedirectUrl_usesConfiguredUrls() {
            Invoice target = invoice(12L, null, InvoicePaymentStatus.PENDING);
            when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenReturn(payOsLink());

            PaymentInitResponse response = payOsInvoicePaymentService.initiatePayOsPayment(target, null);

            ArgumentCaptor<CreatePaymentLinkRequest> captor =
                    ArgumentCaptor.forClass(CreatePaymentLinkRequest.class);
            verify(paymentRequestsService).create(captor.capture());
            assertEquals("https://culturequest.vn/payment/success", captor.getValue().getReturnUrl());
            assertEquals("https://culturequest.vn/payment/cancel", captor.getValue().getCancelUrl());
            assertEquals(199_000L, captor.getValue().getAmount());
            assertEquals("https://culturequest.vn/payment/success", response.getDeeplink());
        }

        // UTCID03 - Normal: redirectUrl không có query -> URL hủy được nối bằng dấu "?"
        @Test
        void initiatePayOsPayment_redirectUrlWithoutQuery_appendsCancelWithQuestionMark() {
            Invoice target = invoice(12L, null, InvoicePaymentStatus.PENDING);
            when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenReturn(payOsLink());

            payOsInvoicePaymentService.initiatePayOsPayment(target, "culturequest://payment");

            ArgumentCaptor<CreatePaymentLinkRequest> captor =
                    ArgumentCaptor.forClass(CreatePaymentLinkRequest.class);
            verify(paymentRequestsService).create(captor.capture());
            assertEquals("culturequest://payment", captor.getValue().getReturnUrl());
            assertEquals("culturequest://payment?cancelled=true", captor.getValue().getCancelUrl());
        }

        // UTCID04 - Boundary: redirectUrl đã có query -> URL hủy được nối bằng dấu "&"
        @Test
        void initiatePayOsPayment_redirectUrlWithQuery_appendsCancelWithAmpersand() {
            Invoice target = invoice(12L, null, InvoicePaymentStatus.PENDING);
            when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenReturn(payOsLink());

            payOsInvoicePaymentService.initiatePayOsPayment(target, "culturequest://payment?from=app");

            ArgumentCaptor<CreatePaymentLinkRequest> captor =
                    ArgumentCaptor.forClass(CreatePaymentLinkRequest.class);
            verify(paymentRequestsService).create(captor.capture());
            assertEquals("culturequest://payment?from=app&cancelled=true", captor.getValue().getCancelUrl());
        }

        // UTCID05 - Boundary: redirectUrl là chuỗi rỗng -> coi như không truyền, dùng URL mặc định
        @Test
        void initiatePayOsPayment_blankRedirectUrl_fallsBackToConfiguredUrls() {
            Invoice target = invoice(12L, null, InvoicePaymentStatus.PENDING);
            when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenReturn(payOsLink());

            payOsInvoicePaymentService.initiatePayOsPayment(target, "   ");

            ArgumentCaptor<CreatePaymentLinkRequest> captor =
                    ArgumentCaptor.forClass(CreatePaymentLinkRequest.class);
            verify(paymentRequestsService).create(captor.capture());
            assertEquals("https://culturequest.vn/payment/cancel", captor.getValue().getCancelUrl());
        }

        // UTCID06 - Normal: tạo link thành công -> lưu hóa đơn và mở giao dịch PENDING để đối soát
        @Test
        void initiatePayOsPayment_success_savesInvoiceAndPendingTransaction() {
            Invoice target = invoice(12L, null, InvoicePaymentStatus.PENDING);
            when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenReturn(payOsLink());

            PaymentInitResponse response = payOsInvoicePaymentService.initiatePayOsPayment(target, null);

            assertEquals(PaymentGateway.PAYOS, target.getPaymentGateway());
            assertEquals("f1a2b3c4d5", target.getPayosPaymentLinkId());
            assertNotNull(target.getPayosOrderCode());
            verify(invoiceRepository).save(target);

            ArgumentCaptor<SystemTransaction> txCaptor = ArgumentCaptor.forClass(SystemTransaction.class);
            verify(systemTransactionRepository).save(txCaptor.capture());
            assertEquals(SystemTransactionStatus.PENDING, txCaptor.getValue().getStatus());
            assertEquals(SystemTransactionType.PAYMENT, txCaptor.getValue().getTransactionType());
            assertEquals(199_000L, txCaptor.getValue().getAmount());
            assertEquals(String.valueOf(target.getPayosOrderCode()), txCaptor.getValue().getGatewayRef());

            assertEquals(12L, response.getSubscriptionId());
            assertEquals("https://pay.payos.vn/web/f1a2b3c4d5", response.getCheckoutUrl());
            assertEquals("00020101021238570010A000000727", response.getQrCode());
        }

        // UTCID07 - Boundary: mã đơn PayOS phải được sinh duy nhất theo invoiceId (invoiceId * 100000 + giây)
        @Test
        void initiatePayOsPayment_orderCodeIsDerivedFromInvoiceId() {
            Invoice target = invoice(12L, null, InvoicePaymentStatus.PENDING);
            when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class))).thenReturn(payOsLink());

            payOsInvoicePaymentService.initiatePayOsPayment(target, null);

            long orderCode = target.getPayosOrderCode();
            assertEquals(12L, orderCode / 100_000L);
            assertTrue(orderCode % 100_000L < 86_400L);
        }

        // UTCID08 - Abnormal: PayOS trả lỗi -> ném BusinessException, không lưu giao dịch treo
        @Test
        void initiatePayOsPayment_payOsThrows_throwsBusinessException() {
            Invoice target = invoice(12L, null, InvoicePaymentStatus.PENDING);
            when(paymentRequestsService.create(any(CreatePaymentLinkRequest.class)))
                    .thenThrow(new PayOSException("Chữ ký không hợp lệ"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> payOsInvoicePaymentService.initiatePayOsPayment(target, null));

            assertTrue(ex.getMessage().startsWith("Không thể tạo link thanh toán PayOS:"));
            verify(invoiceRepository, never()).save(any());
            verify(systemTransactionRepository, never()).save(any());
        }
    }

    // =====================================================================
    // Function: reconcileInvoiceWithPayOs
    // =====================================================================
    @Nested
    @DisplayName("reconcileInvoiceWithPayOs")
    class ReconcileInvoiceWithPayOsTest {

        // UTCID01 - Normal: hóa đơn đã PAID -> trả true ngay, không gọi PayOS
        @Test
        void reconcileInvoiceWithPayOs_alreadyPaid_returnsTrueWithoutCallingPayOs() {
            Invoice paid = invoice(12L, 1200001L, InvoicePaymentStatus.PAID);

            assertTrue(payOsInvoicePaymentService.reconcileInvoiceWithPayOs(paid));

            verify(paymentRequestsService, never()).get(anyLong());
        }

        // UTCID02 - Abnormal: chưa khởi tạo thanh toán (không có mã đơn) -> báo lỗi nghiệp vụ
        @Test
        void reconcileInvoiceWithPayOs_noOrderCode_throwsNotInitiated() {
            Invoice target = invoice(12L, null, InvoicePaymentStatus.PENDING);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> payOsInvoicePaymentService.reconcileInvoiceWithPayOs(target));

            assertEquals("Hóa đơn chưa được khởi tạo thanh toán", ex.getMessage());
        }

        // UTCID03 - Normal: PayOS báo PAID -> kích hoạt hóa đơn với mã tham chiếu ngân hàng
        @Test
        void reconcileInvoiceWithPayOs_statusPaid_activatesInvoiceWithReference() {
            Invoice target = invoice(12L, 1200001L, InvoicePaymentStatus.PENDING);
            when(paymentRequestsService.get(1200001L))
                    .thenReturn(paymentLink(PaymentLinkStatus.PAID, "FT26031500123"));

            assertTrue(payOsInvoicePaymentService.reconcileInvoiceWithPayOs(target));

            verify(invoiceActivationService).markInvoicePaid(target, "FT26031500123");
        }

        // UTCID04 - Boundary: PayOS báo PAID nhưng danh sách giao dịch rỗng -> reference = null
        @Test
        void reconcileInvoiceWithPayOs_paidWithoutTransactions_passesNullReference() {
            Invoice target = invoice(12L, 1200001L, InvoicePaymentStatus.PENDING);
            PaymentLink link = paymentLink(PaymentLinkStatus.PAID, null);
            link.setTransactions(List.of());
            when(paymentRequestsService.get(1200001L)).thenReturn(link);

            assertTrue(payOsInvoicePaymentService.reconcileInvoiceWithPayOs(target));

            verify(invoiceActivationService).markInvoicePaid(target, null);
        }

        // UTCID05 - Normal: PayOS báo CANCELLED -> đánh dấu hóa đơn thất bại, trả false
        @Test
        void reconcileInvoiceWithPayOs_statusCancelled_marksFailed() {
            Invoice target = invoice(12L, 1200001L, InvoicePaymentStatus.PENDING);
            when(paymentRequestsService.get(1200001L))
                    .thenReturn(paymentLink(PaymentLinkStatus.CANCELLED, null));

            assertFalse(payOsInvoicePaymentService.reconcileInvoiceWithPayOs(target));

            verify(invoiceActivationService).markInvoiceFailed(target);
            verify(invoiceActivationService, never()).markInvoicePaid(any(), any());
        }

        // UTCID06 - Normal: PayOS báo EXPIRED -> đánh dấu thất bại
        @Test
        void reconcileInvoiceWithPayOs_statusExpired_marksFailed() {
            Invoice target = invoice(12L, 1200001L, InvoicePaymentStatus.PENDING);
            when(paymentRequestsService.get(1200001L))
                    .thenReturn(paymentLink(PaymentLinkStatus.EXPIRED, null));

            assertFalse(payOsInvoicePaymentService.reconcileInvoiceWithPayOs(target));

            verify(invoiceActivationService).markInvoiceFailed(target);
        }

        // UTCID07 - Boundary: PayOS còn PENDING (user chưa trả) -> trả false, KHÔNG đánh dấu thất bại
        @Test
        void reconcileInvoiceWithPayOs_statusPending_returnsFalseWithoutMarkingFailed() {
            Invoice target = invoice(12L, 1200001L, InvoicePaymentStatus.PENDING);
            when(paymentRequestsService.get(1200001L))
                    .thenReturn(paymentLink(PaymentLinkStatus.PENDING, null));

            assertFalse(payOsInvoicePaymentService.reconcileInvoiceWithPayOs(target));

            verify(invoiceActivationService, never()).markInvoiceFailed(any());
            verify(invoiceActivationService, never()).markInvoicePaid(any(), any());
        }

        // UTCID08 - Abnormal: PayOS lỗi kết nối -> ném BusinessException để controller trả 400
        @Test
        void reconcileInvoiceWithPayOs_payOsThrows_throwsBusinessException() {
            Invoice target = invoice(12L, 1200001L, InvoicePaymentStatus.PENDING);
            when(paymentRequestsService.get(1200001L))
                    .thenThrow(new PayOSException("Timeout khi gọi PayOS"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> payOsInvoicePaymentService.reconcileInvoiceWithPayOs(target));

            assertTrue(ex.getMessage().startsWith("Không thể kiểm tra trạng thái thanh toán từ PayOS:"));
        }
    }
}
