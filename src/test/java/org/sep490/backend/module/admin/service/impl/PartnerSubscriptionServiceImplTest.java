package org.sep490.backend.module.admin.service.impl;

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
import org.sep490.backend.common.service.PayOsInvoicePaymentService;
import org.sep490.backend.common.service.TransactionCompensationService;
import org.sep490.backend.config.keycloak.KeyCloakAuthClient;
import org.sep490.backend.config.payos.PayOsProperties;
import org.sep490.backend.module.admin.dto.request.PartnerSubscriptionRequest;
import org.sep490.backend.module.admin.dto.response.PaymentInitResponse;
import org.sep490.backend.module.admin.dto.response.PartnerSubscriptionResponse;
import org.sep490.backend.module.admin.entity.Invoice;
import org.sep490.backend.module.admin.entity.PartnerApproval;
import org.sep490.backend.module.admin.entity.PartnerInfo;
import org.sep490.backend.module.admin.entity.SubscriptionPlan;
import org.sep490.backend.module.admin.entity.enumeration.BillingCycleEnum;
import org.sep490.backend.module.admin.entity.enumeration.InvoicePaymentStatus;
import org.sep490.backend.module.admin.entity.enumeration.InvoiceStatus;
import org.sep490.backend.module.admin.entity.enumeration.PartnerApprovalStatus;
import org.sep490.backend.module.admin.entity.enumeration.PartnerInfoStatus;
import org.sep490.backend.module.admin.mapper.PartnerSubscriptionMapper;
import org.sep490.backend.module.admin.repository.*;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.content.service.inter.MediaService;
import org.sep490.backend.module.content.service.inter.S3Service;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import vn.payos.PayOS;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho ĐĂNG KÝ GÓI ĐỐI TÁC (Partner Subscription).
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartnerSubscriptionServiceImplTest {

    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private PartnerInfoRepository partnerInfoRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PartnerApprovalRepository partnerApprovalRepository;
    @Mock private SubscriptionUsageRepository subscriptionUsageRepository;
    @Mock private SystemTransactionRepository systemTransactionRepository;
    @Mock private PartnerSubscriptionMapper subscriptionMapper;
    @Mock private UserService userService;
    @Mock private KeyCloakAuthClient keyCloakAuthClient;
    @Mock private UserRepository userRepository;
    @Mock private S3Service s3Service;
    @Mock private MediaService mediaService;
    @Mock private JavaMailSender mailSender;
    @Mock private PayOsProperties payOsProperties;
    @Mock private PayOS payOS;
    @Mock private PlanRuleRepository planRuleRepository;
    @Mock private PayOsInvoicePaymentService payOsInvoicePaymentService;
    @Mock private InvoiceActivationService invoiceActivationService;
    @Mock private TransactionCompensationService txCompensation;

    @InjectMocks private PartnerSubscriptionServiceImpl partnerSubscriptionService;

    private static User user(Long userId, String email) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(email);
        user.setUsername("partner01");
        user.setDisplayName("Quán Cà Phê Sáng");
        return user;
    }

    /** Gói CHUẨN: 200.000đ/tháng, 2.000.000đ/năm. */
    private static SubscriptionPlan plan(Long priceMonthly, Long priceYearly) {
        return SubscriptionPlan.builder()
                .subscriptionPlanId(1L)
                .subscriptionPlanName("Gói CHUẨN")
                .priceMonthly(priceMonthly)
                .priceYearly(priceYearly)
                .build();
    }

    private static MockMultipartFile documentFile() {
        return new MockMultipartFile("documentFile", "giay-phep.pdf",
                "application/pdf", new byte[2048]);
    }

    private static PartnerSubscriptionRequest request(BillingCycleEnum cycle,
                                                      MockMultipartFile document) {
        return PartnerSubscriptionRequest.builder()
                .subscriptionPlanId(1L)
                .shopName("Cà Phê Sáng")
                .shopEmail("shop@gmail.com")
                .address("12 Nguyễn Huệ, Quận 1, TP.HCM")
                .longitude(106.7009)
                .latitude(10.7769)
                .documentFile(document)
                .billingCycle(cycle)
                .build();
    }

    // =====================================================================
    // Function: registerSubscription
    // =====================================================================
    @Nested
    @DisplayName("registerSubscription")
    class RegisterSubscriptionTest {

        // UTCID01 - Abnormal: gói dịch vụ không tồn tại
        @Test
        void registerSubscription_planNotFound_throwsPlanNotFound() {
            when(userService.getCurrentUser()).thenReturn(user(1L, "owner@gmail.com"));
            when(planRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> partnerSubscriptionService.registerSubscription(
                            request(BillingCycleEnum.MONTHLY, documentFile())));

            assertEquals("Gói đăng ký không tồn tại", ex.getMessage());
            verify(invoiceRepository, never()).save(any());
        }

        // UTCID02 - Abnormal: email shop đã được dùng cho partner_info khác
        @Test
        void registerSubscription_shopEmailUsedByAnotherPartner_throwsDuplicateEmail() {
            when(userService.getCurrentUser()).thenReturn(user(1L, "owner@gmail.com"));
            when(planRepository.findById(1L)).thenReturn(Optional.of(plan(200000L, 2000000L)));
            when(partnerInfoRepository.existsByShopEmail("shop@gmail.com")).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> partnerSubscriptionService.registerSubscription(
                            request(BillingCycleEnum.MONTHLY, documentFile())));

            assertEquals("Email quản lý shop này đã được đăng ký cho một tài khoản khác.",
                    ex.getMessage());
            verify(partnerInfoRepository, never()).save(any());
        }

        // UTCID03 - Abnormal: email shop đã trùng với tài khoản người dùng khác
        @Test
        void registerSubscription_shopEmailUsedByAnotherUser_throwsDuplicateEmail() {
            when(userService.getCurrentUser()).thenReturn(user(1L, "owner@gmail.com"));
            when(planRepository.findById(1L)).thenReturn(Optional.of(plan(200000L, 2000000L)));
            when(partnerInfoRepository.existsByShopEmail(anyString())).thenReturn(false);
            when(userRepository.existsByEmail("shop@gmail.com")).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> partnerSubscriptionService.registerSubscription(
                            request(BillingCycleEnum.MONTHLY, documentFile())));

            assertEquals("Email quản lý shop này đã được đăng ký cho một tài khoản khác.",
                    ex.getMessage());
        }

        // UTCID04 - Abnormal: gói tháng nhưng chưa cấu hình giá (priceMonthly = null -> 0)
        @Test
        void registerSubscription_monthlyPriceNull_throwsInvalidPrice() {
            when(userService.getCurrentUser()).thenReturn(user(1L, "owner@gmail.com"));
            when(planRepository.findById(1L)).thenReturn(Optional.of(plan(null, 2000000L)));
            when(partnerInfoRepository.existsByShopEmail(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> partnerSubscriptionService.registerSubscription(
                            request(BillingCycleEnum.MONTHLY, documentFile())));

            assertEquals("Giá gói không hợp lệ.", ex.getMessage());
        }

        // UTCID05 - Boundary: giá gói = 0 -> không hợp lệ
        @Test
        void registerSubscription_zeroPrice_throwsInvalidPrice() {
            when(userService.getCurrentUser()).thenReturn(user(1L, "owner@gmail.com"));
            when(planRepository.findById(1L)).thenReturn(Optional.of(plan(0L, 2000000L)));
            when(partnerInfoRepository.existsByShopEmail(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> partnerSubscriptionService.registerSubscription(
                            request(BillingCycleEnum.MONTHLY, documentFile())));

            assertEquals("Giá gói không hợp lệ.", ex.getMessage());
        }

        // UTCID06 - Abnormal: thiếu giấy tờ xác minh
        @Test
        void registerSubscription_missingDocument_throwsDocumentRequired() {
            when(userService.getCurrentUser()).thenReturn(user(1L, "owner@gmail.com"));
            when(planRepository.findById(1L)).thenReturn(Optional.of(plan(200000L, 2000000L)));
            when(partnerInfoRepository.existsByShopEmail(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(subscriptionMapper.toPartnerInfo(any())).thenReturn(new PartnerInfo());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> partnerSubscriptionService.registerSubscription(
                            request(BillingCycleEnum.MONTHLY, null)));

            assertEquals("Giấy tờ xác minh là bắt buộc đối với đối tác", ex.getMessage());
            verify(partnerInfoRepository, never()).save(any());
        }

        // UTCID07 - Abnormal: upload giấy tờ lên S3 lỗi
        @Test
        void registerSubscription_s3UploadFails_throwsUploadError() throws IOException {
            when(userService.getCurrentUser()).thenReturn(user(1L, "owner@gmail.com"));
            when(planRepository.findById(1L)).thenReturn(Optional.of(plan(200000L, 2000000L)));
            when(partnerInfoRepository.existsByShopEmail(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(subscriptionMapper.toPartnerInfo(any())).thenReturn(new PartnerInfo());
            when(s3Service.uploadFile(any(), anyString()))
                    .thenThrow(new IOException("S3 timeout"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> partnerSubscriptionService.registerSubscription(
                            request(BillingCycleEnum.MONTHLY, documentFile())));

            assertTrue(ex.getMessage().startsWith("Lỗi xảy ra khi tải lên tài liệu xác minh lên S3: "));
        }

        // UTCID08 - Normal: đăng ký gói THÁNG thành công -> hóa đơn PENDING, số tiền 200.000
        @Test
        void registerSubscription_monthlyValid_createsPendingInvoiceWithMonthlyPrice() throws IOException {
            User owner = user(1L, "owner@gmail.com");
            PartnerInfo partnerInfo = new PartnerInfo();
            when(userService.getCurrentUser()).thenReturn(owner);
            when(planRepository.findById(1L)).thenReturn(Optional.of(plan(200000L, 2000000L)));
            when(partnerInfoRepository.existsByShopEmail(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(subscriptionMapper.toPartnerInfo(any())).thenReturn(partnerInfo);
            when(s3Service.uploadFile(any(), anyString())).thenReturn("https://s3/doc.pdf");
            when(partnerInfoRepository.save(any(PartnerInfo.class))).thenAnswer(inv -> inv.getArgument(0));
            when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

            partnerSubscriptionService.registerSubscription(
                    request(BillingCycleEnum.MONTHLY, documentFile()));

            assertSame(owner, partnerInfo.getUser());
            assertEquals(PartnerInfoStatus.INACTIVE, partnerInfo.getStatus());
            assertEquals("https://s3/doc.pdf", partnerInfo.getDocumentUrl());

            ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
            verify(invoiceRepository).save(captor.capture());
            Invoice invoice = captor.getValue();
            assertEquals(200000L, invoice.getPaidAmount());
            assertEquals(InvoiceStatus.PENDING, invoice.getStatus());
            assertEquals(InvoicePaymentStatus.PENDING, invoice.getPaymentStatus());
            assertTrue(invoice.getInvoiceCode().startsWith("INV"));
        }

        // UTCID09 - Normal: đăng ký gói NĂM -> lấy giá theo năm 2.000.000
        @Test
        void registerSubscription_yearlyValid_usesYearlyPrice() throws IOException {
            when(userService.getCurrentUser()).thenReturn(user(1L, "owner@gmail.com"));
            when(planRepository.findById(1L)).thenReturn(Optional.of(plan(200000L, 2000000L)));
            when(partnerInfoRepository.existsByShopEmail(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(subscriptionMapper.toPartnerInfo(any())).thenReturn(new PartnerInfo());
            when(s3Service.uploadFile(any(), anyString())).thenReturn("https://s3/doc.pdf");
            when(partnerInfoRepository.save(any(PartnerInfo.class))).thenAnswer(inv -> inv.getArgument(0));
            when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

            partnerSubscriptionService.registerSubscription(
                    request(BillingCycleEnum.YEARLY, documentFile()));

            ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
            verify(invoiceRepository).save(captor.capture());
            assertEquals(2000000L, captor.getValue().getPaidAmount());
            assertEquals(BillingCycleEnum.YEARLY, captor.getValue().getBillingCycle());
        }
    }

    // =====================================================================
    // Function: verifiedSubscription
    // =====================================================================
    @Nested
    @DisplayName("verifiedSubscription")
    class VerifiedSubscriptionTest {

        private static Invoice invoice(InvoiceStatus status, BillingCycleEnum cycle, User premiumUser) {
            PartnerInfo partnerInfo = PartnerInfo.builder()
                    .partnerInfoId(5L)
                    .user(user(1L, "owner@gmail.com"))
                    .shopName("Cà Phê Sáng")
                    .shopEmail("shop@gmail.com")
                    .status(PartnerInfoStatus.INACTIVE)
                    .build();
            return Invoice.builder()
                    .invoiceId(10L)
                    .partnerInfo(partnerInfo)
                    .user(premiumUser)
                    .subscriptionPlan(plan(200000L, 2000000L))
                    .billingCycle(cycle)
                    .status(status)
                    .paidAmount(200000L)
                    .invoiceCode("INV123")
                    .build();
        }

        // UTCID01 - Abnormal: hóa đơn không tồn tại
        @Test
        void verifiedSubscription_invoiceNotFound_throwsNotFound() {
            when(invoiceRepository.findById(10L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> partnerSubscriptionService.verifiedSubscription(10L, true));

            assertEquals("Hóa đơn đăng ký gói không tồn tại", ex.getMessage());
        }

        // UTCID02 - Abnormal: đây là hóa đơn Premium của người dùng, không phải luồng đối tác
        @Test
        void verifiedSubscription_premiumInvoice_throwsNotForAdminApproval() {
            when(invoiceRepository.findById(10L)).thenReturn(Optional.of(
                    invoice(InvoiceStatus.PENDING, BillingCycleEnum.MONTHLY, user(2L, "u@gmail.com"))));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> partnerSubscriptionService.verifiedSubscription(10L, true));

            assertEquals("Hóa đơn Premium không cần Admin duyệt", ex.getMessage());
        }

        // UTCID03 - Abnormal: hóa đơn không ở trạng thái chờ duyệt
        @Test
        void verifiedSubscription_notPendingStatus_throwsWrongStatus() {
            when(invoiceRepository.findById(10L)).thenReturn(Optional.of(
                    invoice(InvoiceStatus.ACTIVE, BillingCycleEnum.MONTHLY, null)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> partnerSubscriptionService.verifiedSubscription(10L, true));

            assertEquals("Chỉ có thể duyệt hóa đơn dịch vụ đang ở trạng thái chờ duyệt", ex.getMessage());
        }

        // UTCID04 - Abnormal: duyệt nhưng email shop đã trùng người dùng khác
        @Test
        void verifiedSubscription_approveWithDuplicateEmail_throwsDuplicate() {
            when(invoiceRepository.findById(10L)).thenReturn(Optional.of(
                    invoice(InvoiceStatus.PENDING, BillingCycleEnum.MONTHLY, null)));
            when(userRepository.existsByEmail("shop@gmail.com")).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> partnerSubscriptionService.verifiedSubscription(10L, true));

            assertEquals("Không thể duyệt: Email shop cung cấp đã bị trùng lặp với người dùng khác "
                    + "trong hệ thống.", ex.getMessage());
            verify(keyCloakAuthClient, never())
                    .createUser(anyString(), anyString(), anyString(), anyString(), anyList());
        }

        // UTCID05 - Abnormal: từ chối duyệt -> hóa đơn CANCELLED, partner INACTIVE
        @Test
        void verifiedSubscription_rejected_cancelsInvoiceAndRecordsRejection() {
            Invoice target = invoice(InvoiceStatus.PENDING, BillingCycleEnum.MONTHLY, null);
            when(invoiceRepository.findById(10L)).thenReturn(Optional.of(target));
            when(userService.getCurrentUser()).thenReturn(user(99L, "admin@gmail.com"));
            when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

            partnerSubscriptionService.verifiedSubscription(10L, false);

            assertEquals(InvoiceStatus.CANCELLED, target.getStatus());
            assertEquals(PartnerInfoStatus.INACTIVE, target.getPartnerInfo().getStatus());

            ArgumentCaptor<PartnerApproval> captor = ArgumentCaptor.forClass(PartnerApproval.class);
            verify(partnerApprovalRepository).save(captor.capture());
            assertEquals(PartnerApprovalStatus.REJECTED, captor.getValue().getApprovalStatus());
            verify(keyCloakAuthClient, never())
                    .createUser(anyString(), anyString(), anyString(), anyString(), anyList());
        }

        // UTCID06 - Normal: duyệt gói THÁNG -> ACTIVE, hạn +1 tháng, tạo tài khoản shop
        @Test
        void verifiedSubscription_approveMonthly_activatesWithOneMonthEndDate() {
            Invoice target = invoice(InvoiceStatus.PENDING, BillingCycleEnum.MONTHLY, null);
            when(invoiceRepository.findById(10L)).thenReturn(Optional.of(target));
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userService.getCurrentUser()).thenReturn(user(99L, "admin@gmail.com"));
            when(planRuleRepository.findBySubscriptionPlan_SubscriptionPlanId(1L)).thenReturn(List.of());
            when(keyCloakAuthClient.createUser(anyString(), anyString(), anyString(), anyString(), anyList()))
                    .thenReturn("kc-shop-001");
            when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

            partnerSubscriptionService.verifiedSubscription(10L, true);

            assertEquals(InvoiceStatus.ACTIVE, target.getStatus());
            assertEquals(PartnerInfoStatus.ACTIVE, target.getPartnerInfo().getStatus());
            assertNotNull(target.getStartDate());
            assertEquals(target.getStartDate().plusMonths(1), target.getEndDate());

            ArgumentCaptor<PartnerApproval> captor = ArgumentCaptor.forClass(PartnerApproval.class);
            verify(partnerApprovalRepository).save(captor.capture());
            assertEquals(PartnerApprovalStatus.APPROVED, captor.getValue().getApprovalStatus());
            verify(txCompensation).runOnRollback(anyString(), any(Runnable.class));
        }

        // UTCID07 - Normal: duyệt gói NĂM -> hạn +1 năm
        @Test
        void verifiedSubscription_approveYearly_setsOneYearEndDate() {
            Invoice target = invoice(InvoiceStatus.PENDING, BillingCycleEnum.YEARLY, null);
            when(invoiceRepository.findById(10L)).thenReturn(Optional.of(target));
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userService.getCurrentUser()).thenReturn(user(99L, "admin@gmail.com"));
            when(planRuleRepository.findBySubscriptionPlan_SubscriptionPlanId(anyLong()))
                    .thenReturn(List.of());
            when(keyCloakAuthClient.createUser(anyString(), anyString(), anyString(), anyString(), anyList()))
                    .thenReturn("kc-shop-001");
            when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

            partnerSubscriptionService.verifiedSubscription(10L, true);

            assertEquals(target.getStartDate().plusYears(1), target.getEndDate());
        }

        // UTCID08 - Abnormal: tạo tài khoản Keycloak cho shop thất bại
        @Test
        void verifiedSubscription_keycloakCreateFails_throwsSystemError() {
            Invoice target = invoice(InvoiceStatus.PENDING, BillingCycleEnum.MONTHLY, null);
            when(invoiceRepository.findById(10L)).thenReturn(Optional.of(target));
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userService.getCurrentUser()).thenReturn(user(99L, "admin@gmail.com"));
            when(planRuleRepository.findBySubscriptionPlan_SubscriptionPlanId(anyLong()))
                    .thenReturn(List.of());
            when(keyCloakAuthClient.createUser(anyString(), anyString(), anyString(), anyString(), anyList()))
                    .thenThrow(new RuntimeException("Keycloak down"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> partnerSubscriptionService.verifiedSubscription(10L, true));

            assertTrue(ex.getMessage()
                    .startsWith("Lỗi hệ thống khi tạo tài khoản quản lý cho Partner: "));
            verify(txCompensation, never()).runOnRollback(anyString(), any(Runnable.class));
        }
    }

    // =====================================================================
    // Function: initiatePayment
    // =====================================================================
    @Nested
    @DisplayName("initiatePayment")
    class InitiatePaymentTest {

        private static Invoice payableInvoice(User owner, InvoicePaymentStatus paymentStatus,
                                              String documentUrl) {
            PartnerInfo partnerInfo = PartnerInfo.builder()
                    .partnerInfoId(5L)
                    .user(owner)
                    .shopEmail("shop@gmail.com")
                    .documentUrl(documentUrl)
                    .build();
            return Invoice.builder()
                    .invoiceId(10L)
                    .partnerInfo(partnerInfo)
                    .paymentStatus(paymentStatus)
                    .paidAmount(200000L)
                    .build();
        }

        // UTCID01 - Abnormal: hóa đơn không tồn tại
        @Test
        void initiatePayment_invoiceNotFound_throwsNotFound() {
            when(userService.getCurrentUser()).thenReturn(user(1L, "owner@gmail.com"));
            when(invoiceRepository.findById(10L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> partnerSubscriptionService.initiatePayment(10L, "https://app/return", "PAYOS"));

            assertEquals("Hóa đơn không tồn tại", ex.getMessage());
        }

        // UTCID02 - Abnormal: hóa đơn không thuộc luồng đối tác (partnerInfo = null)
        @Test
        void initiatePayment_notPartnerInvoice_throwsWrongFlow() {
            when(userService.getCurrentUser()).thenReturn(user(1L, "owner@gmail.com"));
            when(invoiceRepository.findById(10L))
                    .thenReturn(Optional.of(Invoice.builder().invoiceId(10L).build()));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> partnerSubscriptionService.initiatePayment(10L, "https://app/return", "PAYOS"));

            assertEquals("Hóa đơn này không thuộc luồng đối tác", ex.getMessage());
        }

        // UTCID03 - Abnormal: hóa đơn của đối tác khác -> không có quyền
        @Test
        void initiatePayment_notOwner_throwsNoPermission() {
            when(userService.getCurrentUser()).thenReturn(user(2L, "khac@gmail.com"));
            when(invoiceRepository.findById(10L)).thenReturn(Optional.of(
                    payableInvoice(user(1L, "owner@gmail.com"),
                            InvoicePaymentStatus.PENDING, "https://s3/doc.pdf")));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> partnerSubscriptionService.initiatePayment(10L, "https://app/return", "PAYOS"));

            assertEquals("Bạn không có quyền thực hiện thao tác này.", ex.getMessage());
            verify(payOsInvoicePaymentService, never()).initiatePayOsPayment(any(), anyString());
        }

        // UTCID04 - Abnormal: hóa đơn đã thanh toán rồi
        @Test
        void initiatePayment_alreadyPaid_throwsAlreadyPaid() {
            User owner = user(1L, "owner@gmail.com");
            when(userService.getCurrentUser()).thenReturn(owner);
            when(invoiceRepository.findById(10L)).thenReturn(Optional.of(
                    payableInvoice(owner, InvoicePaymentStatus.PAID, "https://s3/doc.pdf")));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> partnerSubscriptionService.initiatePayment(10L, "https://app/return", "PAYOS"));

            assertEquals("Hóa đơn này đã được thanh toán.", ex.getMessage());
        }

        // UTCID05 - Abnormal: chưa upload giấy tờ xác minh
        @Test
        void initiatePayment_missingDocument_throwsDocumentRequired() {
            User owner = user(1L, "owner@gmail.com");
            when(userService.getCurrentUser()).thenReturn(owner);
            when(invoiceRepository.findById(10L)).thenReturn(Optional.of(
                    payableInvoice(owner, InvoicePaymentStatus.PENDING, null)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> partnerSubscriptionService.initiatePayment(10L, "https://app/return", "PAYOS"));

            assertEquals("Vui lòng upload đầy đủ giấy tờ trước khi thanh toán.", ex.getMessage());
        }

        // UTCID06 - Abnormal: cổng thanh toán không được hỗ trợ
        @Test
        void initiatePayment_unsupportedGateway_throwsUnsupported() {
            User owner = user(1L, "owner@gmail.com");
            when(userService.getCurrentUser()).thenReturn(owner);
            when(invoiceRepository.findById(10L)).thenReturn(Optional.of(
                    payableInvoice(owner, InvoicePaymentStatus.PENDING, "https://s3/doc.pdf")));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> partnerSubscriptionService.initiatePayment(10L, "https://app/return", "MOMO"));

            assertEquals("Cổng thanh toán không được hỗ trợ: MOMO", ex.getMessage());
            verify(payOsInvoicePaymentService, never()).initiatePayOsPayment(any(), anyString());
        }

        // UTCID07 - Normal: hợp lệ -> ủy quyền cho PayOS tạo link thanh toán
        @Test
        void initiatePayment_valid_delegatesToPayOsService() {
            User owner = user(1L, "owner@gmail.com");
            Invoice target = payableInvoice(owner, InvoicePaymentStatus.PENDING, "https://s3/doc.pdf");
            when(userService.getCurrentUser()).thenReturn(owner);
            when(invoiceRepository.findById(10L)).thenReturn(Optional.of(target));

            PaymentInitResponse expected = mock(PaymentInitResponse.class);
            when(payOsInvoicePaymentService.initiatePayOsPayment(target, "https://app/return"))
                    .thenReturn(expected);

            assertSame(expected, partnerSubscriptionService.initiatePayment(
                    10L, "https://app/return", "PAYOS"));
        }

        // UTCID08 - Boundary: tên cổng viết thường "payos" -> vẫn được chấp nhận
        @Test
        void initiatePayment_lowercaseGateway_isAccepted() {
            User owner = user(1L, "owner@gmail.com");
            Invoice target = payableInvoice(owner, InvoicePaymentStatus.PENDING, "https://s3/doc.pdf");
            when(userService.getCurrentUser()).thenReturn(owner);
            when(invoiceRepository.findById(10L)).thenReturn(Optional.of(target));

            partnerSubscriptionService.initiatePayment(10L, "https://app/return", "payos");

            verify(payOsInvoicePaymentService).initiatePayOsPayment(target, "https://app/return");
        }
    }

    // =====================================================================
    // Function: getAllSubscriptions
    // =====================================================================
    @Nested
    @DisplayName("getAllSubscriptions")
    class GetAllSubscriptionsTest {

        // UTCID01 - Normal: lọc theo trạng thái -> dùng query có filter
        @Test
        void getAllSubscriptions_withStatus_usesFilteredQuery() {
            when(invoiceRepository.findByPartnerInfoIsNotNullAndStatusOrderByCreatedAtDesc(
                    InvoiceStatus.PENDING)).thenReturn(List.of());

            partnerSubscriptionService.getAllSubscriptions(InvoiceStatus.PENDING);

            verify(invoiceRepository)
                    .findByPartnerInfoIsNotNullAndStatusOrderByCreatedAtDesc(InvoiceStatus.PENDING);
            verify(invoiceRepository, never()).findByPartnerInfoIsNotNullOrderByCreatedAtDesc();
        }

        // UTCID02 - Normal: không truyền trạng thái -> lấy tất cả
        @Test
        void getAllSubscriptions_nullStatus_usesUnfilteredQuery() {
            when(invoiceRepository.findByPartnerInfoIsNotNullOrderByCreatedAtDesc())
                    .thenReturn(List.of());

            partnerSubscriptionService.getAllSubscriptions(null);

            verify(invoiceRepository).findByPartnerInfoIsNotNullOrderByCreatedAtDesc();
            verify(invoiceRepository, never())
                    .findByPartnerInfoIsNotNullAndStatusOrderByCreatedAtDesc(any());
        }

        // UTCID03 - Normal: có media đính kèm -> map sang MediaDto trong response
        @Test
        void getAllSubscriptions_withMedias_mapsMediaDtos() {
            PartnerInfo partnerInfo = PartnerInfo.builder()
                    .partnerInfoId(5L)
                    .medias(List.of(new org.sep490.backend.module.content.entity.Media()))
                    .build();
            Invoice target = Invoice.builder().invoiceId(10L).partnerInfo(partnerInfo).build();
            when(invoiceRepository.findByPartnerInfoIsNotNullOrderByCreatedAtDesc())
                    .thenReturn(List.of(target));
            when(subscriptionMapper.toResponse(target)).thenReturn(new PartnerSubscriptionResponse());

            List<PartnerSubscriptionResponse> result =
                    partnerSubscriptionService.getAllSubscriptions(null);

            assertEquals(1, result.size());
            assertEquals(1, result.get(0).getMedias().size());
        }
    }
}
