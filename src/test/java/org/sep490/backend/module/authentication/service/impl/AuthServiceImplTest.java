package org.sep490.backend.module.authentication.service.impl;

import org.sep490.backend.module.authentication.service.AuthTokenService;

import jakarta.mail.internet.MimeMessage;
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
import org.sep490.backend.common.service.TransactionCompensationService;
import org.sep490.backend.config.keycloak.KeyCloakAuthClient;
import org.sep490.backend.config.keycloak.KeyCloakTokenResponse;
import org.sep490.backend.module.authentication.dto.request.ChangePasswordRequest;
import org.sep490.backend.module.authentication.dto.request.ForgotPasswordRequest;
import org.sep490.backend.module.authentication.dto.request.LoginRequest;
import org.sep490.backend.module.authentication.dto.request.RegistrationRequest;
import org.sep490.backend.module.authentication.dto.request.ResetPasswordRequest;
import org.sep490.backend.module.authentication.dto.request.SendOtpRequest;
import org.sep490.backend.module.authentication.dto.request.VerifyOtpRequest;
import org.sep490.backend.module.authentication.dto.response.LoginResponse;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.sep490.backend.module.authentication.mapper.UserMapper;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.user.dto.response.UserProfileResponse;
import org.sep490.backend.module.user.entity.enumeration.LevelStatus;
import org.sep490.backend.module.user.repository.LevelProgressRepository;
import org.sep490.backend.module.user.repository.LevelRepository;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.Jwt;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho luồng chính XÁC THỰC (Authentication).
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private KeyCloakAuthClient keyCloakAuthClient;
    @Mock private TransactionCompensationService txCompensation;
    @Mock private UserMapper userMapper;
    @Mock private JavaMailSender mailSender;
    @Mock private LevelRepository levelRepository;
    @Mock private LevelProgressRepository levelProgressRepository;
    @Mock private AuthTokenService authTokenService;

    @InjectMocks private AuthServiceImpl authService;

    /** Tạo JWT giả để decodeJwtPayload() đọc được sub và realm_access.roles. */
    private static String fakeJwt(String sub, List<String> roles) {
        String rolesJson = roles.stream()
                .map(r -> "\"" + r + "\"")
                .collect(Collectors.joining(","));
        String payload = "{\"sub\":\"" + sub + "\",\"realm_access\":{\"roles\":[" + rolesJson + "]}}";
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return "header." + encoded + ".signature";
    }

    private static KeyCloakTokenResponse tokenWith(String sub, List<String> roles) {
        KeyCloakTokenResponse token = new KeyCloakTokenResponse();
        token.setAccessToken(fakeJwt(sub, roles));
        token.setRefreshToken("refresh-token");
        token.setTokenType("Bearer");
        token.setExpiresIn(300L);
        token.setRefreshExpiresIn(1800L);
        return token;
    }

    private static LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    /** JWT giả cho login mạng xã hội: có sub, email, preferred_username, name và roles. */
    private static KeyCloakTokenResponse socialToken(String sub, String email, List<String> roles) {
        String rolesJson = roles.stream().map(r -> "\"" + r + "\"").collect(Collectors.joining(","));
        StringBuilder payload = new StringBuilder("{\"sub\":\"" + sub + "\",");
        if (email != null) {
            payload.append("\"email\":\"").append(email).append("\",");
        }
        payload.append("\"preferred_username\":\"traveler01\",\"name\":\"Traveler\",")
                .append("\"realm_access\":{\"roles\":[").append(rolesJson).append("]}}");
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));

        KeyCloakTokenResponse token = new KeyCloakTokenResponse();
        token.setAccessToken("header." + encoded + ".signature");
        token.setRefreshToken("refresh-token");
        token.setTokenType("Bearer");
        token.setExpiresIn(300L);
        token.setRefreshExpiresIn(1800L);
        return token;
    }

    /** JWT đã được Resource Server verify, dùng cho luồng social-sync của mobile. */
    private static Jwt verifiedJwt(String sub, String email, String preferredUsername, String name) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(sub)
                .claim("preferred_username", preferredUsername)
                .claim("name", name);
        if (email != null) {
            builder.claim("email", email);
        }
        return builder.build();
    }

    private User captureSavedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------
    // Tai khoan mau dung xuyen suot: du ho so de doi chieu voi bang unit test
    // username "traveler01" | email "a@gmail.com" | Keycloak "kc-001"
    // ---------------------------------------------------------------
    private static User anAccount() {
        User user = new User();
        user.setUserId(7L);
        user.setUsername("traveler01");
        user.setDisplayName("Tran Minh Anh");
        user.setEmail("a@gmail.com");
        user.setKeycloakUserId("kc-001");
        user.setRole(UserRole.EXPLORER);
        user.setStatus(UserStatus.ACTIVE);
        user.setTotalXp(1200);
        user.setTotalPoints(500);
        return user;
    }

    // =====================================================================
    // Function: login
    // =====================================================================
    @Nested
    @DisplayName("login")
    class LoginTest {

        // UTCID01 - Abnormal: username = null
        @Test
        void login_usernameNull_throwsMissingLoginInfo() {
            LoginRequest request = loginRequest(null, "123456");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(request, "mobile"));

            assertEquals("Thiếu thông tin đăng nhập", ex.getMessage());
            verifyNoInteractions(keyCloakAuthClient);
        }

        // UTCID02 - Abnormal: password = null
        @Test
        void login_passwordNull_throwsMissingLoginInfo() {
            LoginRequest request = loginRequest("traveler01", null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(request, "mobile"));

            assertEquals("Thiếu thông tin đăng nhập", ex.getMessage());
            verifyNoInteractions(keyCloakAuthClient);
        }

        // UTCID03 - Abnormal: sai username hoặc password (Keycloak từ chối)
        @Test
        void login_wrongCredentials_throwsInvalidCredentials() {
            LoginRequest request = loginRequest("traveler01", "wrong-password");
            when(keyCloakAuthClient.login(anyString(), anyString()))
                    .thenThrow(new BusinessException("Invalid user credentials"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(request, "mobile"));

            assertEquals("Tên đăng nhập hoặc mật khẩu không chính xác", ex.getMessage());
        }

        // UTCID04 - Abnormal: Keycloak trả về token rỗng
        @Test
        void login_nullTokenResponse_throwsInvalidCredentials() {
            LoginRequest request = loginRequest("traveler01", "123456");
            when(keyCloakAuthClient.login(anyString(), anyString())).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(request, "mobile"));

            assertEquals("Tên đăng nhập hoặc mật khẩu không chính xác", ex.getMessage());
        }

        // UTCID05 - Abnormal: tài khoản không có role hợp lệ
        @Test
        void login_noAllowedRole_throwsNoPermission() {
            LoginRequest request = loginRequest("traveler01", "123456");
            when(keyCloakAuthClient.login(anyString(), anyString()))
                    .thenReturn(tokenWith("kc-001", List.of("OFFLINE_ACCESS")));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(request, "mobile"));

            assertEquals("Tài khoản không có quyền truy cập", ex.getMessage());
        }

        // UTCID06 - Abnormal: EXPLORER đăng nhập vào web portal bị chặn
        @Test
        void login_explorerOnWebClient_throwsForbidden() {
            LoginRequest request = loginRequest("traveler01", "123456");
            when(keyCloakAuthClient.login(anyString(), anyString()))
                    .thenReturn(tokenWith("kc-001", List.of("EXPLORER")));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(request, "web"));

            assertEquals("Bạn không có quyền truy cập", ex.getMessage());
            verify(keyCloakAuthClient).logout("refresh-token");
        }

        // UTCID07 - Abnormal: không tìm thấy user trong DB hệ thống
        @Test
        void login_userNotFoundInDatabase_throwsInvalidCredentials() {
            LoginRequest request = loginRequest("traveler01", "123456");
            when(keyCloakAuthClient.login(anyString(), anyString()))
                    .thenReturn(tokenWith("kc-001", List.of("EXPLORER")));
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(request, "mobile"));

            assertEquals("Tên đăng nhập hoặc mật khẩu không chính xác", ex.getMessage());
        }

        // UTCID08 - Abnormal: tài khoản EXPLORER bị khóa
        @Test
        void login_lockedExplorerAccount_throwsAccountLocked() {
            LoginRequest request = loginRequest("traveler01", "123456");
            when(keyCloakAuthClient.login(anyString(), anyString()))
                    .thenReturn(tokenWith("kc-001", List.of("EXPLORER")));

            User user = anAccount();
            user.setStatus(UserStatus.INACTIVE);
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.of(user));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(request, "mobile"));

            assertEquals("Tài khoản của bạn đã bị khóa hoặc ngừng hoạt động", ex.getMessage());
        }

        // UTCID09 - Abnormal: tài khoản ADMIN bị khóa (message riêng cho admin)
        @Test
        void login_lockedAdminAccount_throwsAdminAccountLocked() {
            LoginRequest request = loginRequest("admin01", "123456");
            when(keyCloakAuthClient.login(anyString(), anyString()))
                    .thenReturn(tokenWith("kc-admin", List.of("ADMIN")));

            User user = anAccount();
            user.setStatus(UserStatus.INACTIVE);
            when(userRepository.findByKeycloakUserId("kc-admin")).thenReturn(Optional.of(user));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(request, "web"));

            assertEquals("Tài khoản admin của bạn đã bị khóa hoặc ngừng hoạt động", ex.getMessage());
        }

        // UTCID10 - Normal: đăng nhập thành công trên mobile
        @Test
        void login_validCredentialsOnMobile_returnsLoginResponse() {
            LoginRequest request = loginRequest("traveler01", "123456");
            KeyCloakTokenResponse token = tokenWith("kc-001", List.of("EXPLORER"));
            when(keyCloakAuthClient.login("traveler01", "123456")).thenReturn(token);

            User user = anAccount();
            user.setStatus(UserStatus.ACTIVE);
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.of(user));

            LoginResponse response = authService.login(request, "mobile");

            assertNotNull(response);
            assertEquals(token.getAccessToken(), response.getAccessToken());
            assertEquals("Bearer", response.getTokenType());
            assertEquals(300L, response.getExpiresIn());
            assertEquals("refresh-token", response.getRefreshToken());
            verify(keyCloakAuthClient, never()).logout(anyString());
        }
    }

    // =====================================================================
    // Function: verifyEmailWithOtp
    // =====================================================================
    @Nested
    @DisplayName("verifyEmailWithOtp")
    class VerifyOtpTest {

        private VerifyOtpRequest otpRequest(String email, String otpCode) {
            VerifyOtpRequest request = new VerifyOtpRequest();
            request.setEmail(email);
            request.setOtpCode(otpCode);
            return request;
        }

        // UTCID01 - Abnormal: chưa từng yêu cầu OTP (hoặc OTP đã hết TTL trên Redis)
        @Test
        void verifyOtp_noOtpRequest_throwsOtpRequestNotFound() {
            when(authTokenService.findOtp("a@gmail.com")).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.verifyEmailWithOtp(otpRequest("a@gmail.com", "123456")));

            assertEquals("Mã OTP không tồn tại hoặc đã hết hiệu lực. Vui lòng yêu cầu gửi lại",
                    ex.getMessage());
        }

        // UTCID02 - Abnormal: nhập sai mã OTP
        @Test
        void verifyOtp_wrongOtpCode_throwsIncorrectOtp() {
            when(authTokenService.findOtp("a@gmail.com")).thenReturn(Optional.of("123456"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.verifyEmailWithOtp(otpRequest("a@gmail.com", "999999")));

            assertEquals("Mã OTP không chính xác", ex.getMessage());
        }

        // UTCID03 - Abnormal: OTP hết hạn. Redis tự xoá theo TTL nên findOtp trả empty,
        // không còn cần kiểm tra isExpired() thủ công như trước.
        @Test
        void verifyOtp_expiredOtp_throwsOtpExpired() {
            when(authTokenService.findOtp("a@gmail.com")).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.verifyEmailWithOtp(otpRequest("a@gmail.com", "123456")));

            assertEquals("Mã OTP không tồn tại hoặc đã hết hiệu lực. Vui lòng yêu cầu gửi lại",
                    ex.getMessage());
        }

        // UTCID04 - Abnormal: OTP hợp lệ nhưng không có user khớp email
        @Test
        void verifyOtp_userNotFound_throwsUserNotFound() {
            when(authTokenService.findOtp("a@gmail.com")).thenReturn(Optional.of("123456"));
            when(userRepository.findByEmailIgnoreCase("a@gmail.com")).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.verifyEmailWithOtp(otpRequest("a@gmail.com", "123456")));

            assertEquals("Không tìm thấy người dùng khớp với email này", ex.getMessage());
        }

        // UTCID05 - Normal: xác thực OTP thành công, tài khoản chuyển sang ACTIVE
        @Test
        void verifyOtp_validOtp_activatesAccount() {
            when(authTokenService.findOtp("a@gmail.com")).thenReturn(Optional.of("123456"));

            User user = anAccount();
            user.setStatus(UserStatus.PENDING);
            when(userRepository.findByEmailIgnoreCase("a@gmail.com")).thenReturn(Optional.of(user));

            authService.verifyEmailWithOtp(otpRequest("a@gmail.com", "123456"));

            assertEquals(UserStatus.ACTIVE, user.getStatus());
            verify(userRepository).save(user);
            verify(authTokenService).deleteOtp("a@gmail.com");
        }

        // UTCID06 - Boundary: OTP có khoảng trắng thừa vẫn được chấp nhận sau khi trim
        @Test
        void verifyOtp_otpWithSurroundingSpaces_isTrimmedAndAccepted() {
            when(authTokenService.findOtp("a@gmail.com")).thenReturn(Optional.of("123456"));

            User user = anAccount();
            user.setStatus(UserStatus.PENDING);
            when(userRepository.findByEmailIgnoreCase("a@gmail.com")).thenReturn(Optional.of(user));

            authService.verifyEmailWithOtp(otpRequest("  a@gmail.com  ", "  123456  "));

            assertEquals(UserStatus.ACTIVE, user.getStatus());
        }

        // UTCID07 - Security: chặn vét cạn OTP (trước đây KHÔNG có giới hạn nào)
        @Test
        void verifyOtp_tooManyAttempts_isBlocked() {
            when(authTokenService.isAttemptExceeded("a@gmail.com")).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.verifyEmailWithOtp(otpRequest("a@gmail.com", "123456")));

            assertEquals("Bạn đã nhập sai quá nhiều lần. Vui lòng yêu cầu mã mới sau ít phút.",
                    ex.getMessage());
            // Bị chặn thì không được đọc OTP nữa
            verify(authTokenService, never()).findOtp(anyString());
        }
    }

    // =====================================================================
    // Function: register
    // =====================================================================
    @Nested
    @DisplayName("register")
    class RegisterTest {

        private RegistrationRequest registrationRequest() {
            RegistrationRequest request = new RegistrationRequest();
            request.setUsername("traveler01");
            request.setEmail("a@gmail.com");
            request.setPassword("123456");
            request.setDisplayName("Traveler");
            return request;
        }

        // UTCID01 - Abnormal: tên đăng nhập đã tồn tại
        @Test
        void register_duplicateUsername_throwsUsernameExists() {
            RegistrationRequest request = registrationRequest();
            when(userRepository.existsByUsername("traveler01")).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.register(request));

            assertEquals("Tên đăng nhập đã tồn tại", ex.getMessage());
            verifyNoInteractions(keyCloakAuthClient);
        }

        // UTCID02 - Abnormal: email đã tồn tại
        @Test
        void register_duplicateEmail_throwsEmailExists() {
            RegistrationRequest request = registrationRequest();
            when(userRepository.existsByUsername("traveler01")).thenReturn(false);
            when(userRepository.existsByEmail("a@gmail.com")).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.register(request));

            assertEquals("Email đã tồn tại", ex.getMessage());
            verifyNoInteractions(keyCloakAuthClient);
        }

        // UTCID03 - Abnormal: tạo user Keycloak thất bại -> lỗi Keycloak được ném ra
        @Test
        void register_keycloakCreateFails_propagatesError() {
            RegistrationRequest request = registrationRequest();
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(keyCloakAuthClient.createUser(anyString(), anyString(), anyString(), anyString(), anyList()))
                    .thenThrow(new BusinessException("Không thể tạo user trên Keycloak"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.register(request));

            assertEquals("Không thể tạo user trên Keycloak", ex.getMessage());
            verify(keyCloakAuthClient, never()).safeDeleteUser(anyString());
            verify(txCompensation, never()).runOnRollback(anyString(), any(Runnable.class));
        }

        // UTCID04 - Abnormal: lỗi sau khi tạo user Keycloak -> rollback user Keycloak
        @Test
        void register_failureAfterKeycloakCreate_rollsBackKeycloakUser() {
            RegistrationRequest request = registrationRequest();
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(keyCloakAuthClient.createUser(anyString(), anyString(), anyString(), anyString(), anyList()))
                    .thenReturn("kc-001");
            when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
            doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.register(request));

            assertTrue(ex.getMessage().startsWith("Đăng ký tài khoản thất bại: "));
            verify(keyCloakAuthClient).safeDeleteUser("kc-001");
            verify(txCompensation).runOnRollback(anyString(), any(Runnable.class));
        }

        // UTCID04b - Abnormal: lỗi xảy ra lúc commit (ngoài try/catch) -> hook rollback vẫn xóa user Keycloak
        @Test
        void register_failureAtCommit_rollbackHookDeletesKeycloakUser() {
            RegistrationRequest request = registrationRequest();
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(keyCloakAuthClient.createUser(anyString(), anyString(), anyString(), anyString(), anyList()))
                    .thenReturn("kc-001");
            when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
            when(userMapper.toEntity(request)).thenReturn(anAccount());
            when(levelRepository.findFirstByStatusOrderByRequiredXpAsc(LevelStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(userMapper.toProfileResponse(any(User.class))).thenReturn(mock(UserProfileResponse.class));

            authService.register(request);

            // Method chạy xong bình thường, nhưng transaction có thể rollback lúc commit.
            // Bắt lại action đã đăng ký và chạy nó -> phải xóa được user Keycloak.
            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(txCompensation).runOnRollback(anyString(), captor.capture());
            captor.getValue().run();

            verify(keyCloakAuthClient).safeDeleteUser("kc-001");
        }

        // UTCID05 - Normal: đăng ký thành công, tài khoản ở trạng thái PENDING
        @Test
        void register_validRequest_createsPendingAccountAndSendsOtp() {
            RegistrationRequest request = registrationRequest();
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(keyCloakAuthClient.createUser(anyString(), anyString(), anyString(), anyString(), anyList()))
                    .thenReturn("kc-001");
            when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

            User mapped = anAccount();
            when(userMapper.toEntity(request)).thenReturn(mapped);
            when(levelRepository.findFirstByStatusOrderByRequiredXpAsc(LevelStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserProfileResponse expected = mock(UserProfileResponse.class);
            when(userMapper.toProfileResponse(any(User.class))).thenReturn(expected);

            UserProfileResponse actual = authService.register(request);

            assertSame(expected, actual);
            assertEquals(UserStatus.PENDING, mapped.getStatus());
            assertEquals("kc-001", mapped.getKeycloakUserId());
            verify(authTokenService).saveOtp(anyString(), anyString());
            verify(mailSender).send(any(MimeMessage.class));
            verify(keyCloakAuthClient, never()).safeDeleteUser(anyString());
        }
    }

    // =====================================================================
    // Function: resendOtp
    // =====================================================================
    @Nested
    @DisplayName("resendOtp")
    class ResendOtpTest {

        private SendOtpRequest sendOtpRequest() {
            SendOtpRequest request = new SendOtpRequest();
            request.setEmail("a@gmail.com");
            return request;
        }

        // Cooldown giờ do Redis quyết định (SET NX EX 30) chứ không tính từ createdAt:
        // acquireResendSlot trả 0 = được gửi, > 0 = số giây còn phải đợi.

        // UTCID01 - Normal: chưa từng gửi OTP -> gửi OTP mới
        @Test
        void resendOtp_noPreviousOtp_sendsNewOtp() {
            when(authTokenService.acquireResendSlot("a@gmail.com")).thenReturn(0L);
            when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

            authService.resendOtp(sendOtpRequest());

            verify(authTokenService).saveOtp(eq("a@gmail.com"), anyString());
            verify(mailSender).send(any(MimeMessage.class));
        }

        // UTCID02 - Abnormal: OTP vừa gửi 10 giây trước (còn 20 giây) -> báo chờ
        @Test
        void resendOtp_within10Seconds_throwsCooldown() {
            when(authTokenService.acquireResendSlot("a@gmail.com")).thenReturn(20L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.resendOtp(sendOtpRequest()));

            assertTrue(ex.getMessage().startsWith("Vui lòng đợi thêm"));
            assertTrue(ex.getMessage().endsWith("giây nữa để yêu cầu gửi lại mã OTP."));
            verify(mailSender, never()).send(any(MimeMessage.class));
        }

        // UTCID03 - Boundary: còn đúng 1 giây cuối -> vẫn báo chờ
        @Test
        void resendOtp_at29Seconds_throwsCooldown() {
            when(authTokenService.acquireResendSlot("a@gmail.com")).thenReturn(1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.resendOtp(sendOtpRequest()));

            assertTrue(ex.getMessage().startsWith("Vui lòng đợi thêm"));
            verify(mailSender, never()).send(any(MimeMessage.class));
        }

        // UTCID04 - Boundary: key vừa hết TTL (trả 0) -> gửi OTP mới
        @Test
        void resendOtp_at30Seconds_sendsNewOtp() {
            when(authTokenService.acquireResendSlot("a@gmail.com")).thenReturn(0L);
            when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

            authService.resendOtp(sendOtpRequest());

            verify(authTokenService).saveOtp(eq("a@gmail.com"), anyString());
            verify(mailSender).send(any(MimeMessage.class));
        }

        // UTCID05 - Normal: đã qua cooldown từ lâu -> gửi OTP mới
        @Test
        void resendOtp_after60Seconds_sendsNewOtp() {
            when(authTokenService.acquireResendSlot("a@gmail.com")).thenReturn(0L);
            when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

            authService.resendOtp(sendOtpRequest());

            verify(authTokenService).saveOtp(eq("a@gmail.com"), anyString());
            verify(mailSender).send(any(MimeMessage.class));
        }
    }

    // =====================================================================
    // Function: forgotPassword
    // =====================================================================
    @Nested
    @DisplayName("forgotPassword")
    class ForgotPasswordTest {

        private ForgotPasswordRequest forgotRequest() {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("a@gmail.com");
            return request;
        }

        // UTCID01 - Abnormal: không tìm thấy email trong hệ thống
        @Test
        void forgotPassword_emailNotFound_throwsCheckEmail() {
            when(userRepository.findByEmailIgnoreCase("a@gmail.com")).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.forgotPassword(forgotRequest()));

            assertEquals("Vui lòng kiểm tra lại email", ex.getMessage());
            verify(authTokenService, never()).savePasswordResetToken(anyString(), anyLong());
        }

        // UTCID02 - Abnormal: tài khoản chưa kích hoạt hoặc bị khóa
        @Test
        void forgotPassword_inactiveAccount_throwsNotActivated() {
            User user = anAccount();
            user.setStatus(UserStatus.INACTIVE);
            when(userRepository.findByEmailIgnoreCase("a@gmail.com")).thenReturn(Optional.of(user));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.forgotPassword(forgotRequest()));

            assertEquals("Tài khoản của bạn chưa được kích hoạt hoặc đã bị khóa", ex.getMessage());
            verify(authTokenService, never()).savePasswordResetToken(anyString(), anyLong());
        }

        // UTCID03 - Normal: tài khoản hợp lệ -> tạo token và gửi email đặt lại mật khẩu
        @Test
        void forgotPassword_activeAccount_savesTokenAndSendsEmail() {
            User user = anAccount();
            user.setUserId(7L);
            user.setStatus(UserStatus.ACTIVE);
            when(userRepository.findByEmailIgnoreCase("a@gmail.com")).thenReturn(Optional.of(user));
            when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

            authService.forgotPassword(forgotRequest());

            // savePasswordResetToken tự vô hiệu token cũ nên không cần deleteByUser riêng
            verify(authTokenService).savePasswordResetToken(anyString(), eq(7L));
            verify(mailSender).send(any(MimeMessage.class));
        }
    }

    // =====================================================================
    // Function: resetPassword
    // =====================================================================
    @Nested
    @DisplayName("resetPassword")
    class ResetPasswordTest {

        private ResetPasswordRequest resetRequest(String newPass, String confirmPass) {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setToken("valid-token");
            request.setNewPassword(newPass);
            request.setConfirmPassword(confirmPass);
            return request;
        }

        // UTCID01 - Abnormal: mật khẩu mới và xác nhận không khớp
        @Test
        void resetPassword_passwordMismatch_throwsMismatch() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.resetPassword(resetRequest("newpass1", "newpass2")));

            assertEquals("Mật khẩu không trùng khớp", ex.getMessage());
        }

        // UTCID02 - Abnormal: token không tồn tại
        @Test
        void resetPassword_tokenNotFound_throwsInvalidLink() {
            when(authTokenService.findUserIdByResetToken("valid-token")).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.resetPassword(resetRequest("newpass1", "newpass1")));

            assertEquals("Liên kết đổi mật khẩu không hợp lệ hoặc đã hết hạn", ex.getMessage());
        }

        // UTCID03 - Abnormal: token hết hạn. Redis tự xoá theo TTL nên
        // findUserIdByResetToken trả empty, gộp chung thông báo với token không hợp lệ.
        @Test
        void resetPassword_expiredToken_throwsExpiredLink() {
            when(authTokenService.findUserIdByResetToken("valid-token")).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.resetPassword(resetRequest("newpass1", "newpass1")));

            assertEquals("Liên kết đổi mật khẩu không hợp lệ hoặc đã hết hạn", ex.getMessage());
            verify(keyCloakAuthClient, never()).resetUserPassword(anyString(), anyString());
        }

        // UTCID04 - Normal: token hợp lệ -> đặt lại mật khẩu trên Keycloak
        @Test
        void resetPassword_validToken_resetsPassword() {
            User user = anAccount();
            user.setUserId(7L);
            user.setKeycloakUserId("kc-001");
            when(authTokenService.findUserIdByResetToken("valid-token")).thenReturn(Optional.of(7L));
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));

            authService.resetPassword(resetRequest("newpass1", "newpass1"));

            verify(keyCloakAuthClient).resetUserPassword("kc-001", "newpass1");
            verify(authTokenService).deletePasswordResetToken("valid-token", 7L);
        }
    }

    // =====================================================================
    // Function: changePassword
    // =====================================================================
    @Nested
    @DisplayName("changePassword")
    class ChangePasswordTest {

        private ChangePasswordRequest changeRequest(String oldPass, String newPass, String confirmPass) {
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setOldPassword(oldPass);
            request.setNewPassword(newPass);
            request.setConfirmPassword(confirmPass);
            return request;
        }

        // UTCID01 - Abnormal: mật khẩu mới và xác nhận không khớp
        @Test
        void changePassword_passwordMismatch_throwsMismatch() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.changePassword("kc-001", changeRequest("old123", "new123", "new999")));

            assertEquals("Mật khẩu mới và xác nhận mật khẩu không khớp", ex.getMessage());
        }

        // UTCID02 - Abnormal: không tìm thấy người dùng
        @Test
        void changePassword_userNotFound_throwsUserNotExist() {
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.changePassword("kc-001", changeRequest("old123", "new123", "new123")));

            assertEquals("Người dùng không tồn tại", ex.getMessage());
        }

        // UTCID03 - Abnormal: tài khoản bị khóa
        @Test
        void changePassword_lockedAccount_throwsLocked() {
            User user = anAccount();
            user.setStatus(UserStatus.INACTIVE);
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.of(user));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.changePassword("kc-001", changeRequest("old123", "new123", "new123")));

            assertEquals("Tài khoản đã bị khóa hoặc ngừng hoạt động", ex.getMessage());
        }

        // UTCID04 - Abnormal: mật khẩu mới trùng mật khẩu cũ
        @Test
        void changePassword_newSameAsOld_throwsSamePassword() {
            User user = anAccount();
            user.setStatus(UserStatus.ACTIVE);
            user.setUsername("traveler01");
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.of(user));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.changePassword("kc-001", changeRequest("same123", "same123", "same123")));

            assertEquals("Mật khẩu mới không được trùng với mật khẩu hiện tại", ex.getMessage());
        }

        // UTCID05 - Abnormal: mật khẩu hiện tại không đúng (Keycloak từ chối)
        @Test
        void changePassword_wrongOldPassword_throwsWrongCurrent() {
            User user = anAccount();
            user.setStatus(UserStatus.ACTIVE);
            user.setUsername("traveler01");
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.of(user));
            when(keyCloakAuthClient.login("traveler01", "wrongOld"))
                    .thenThrow(new BusinessException("Invalid user credentials"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.changePassword("kc-001", changeRequest("wrongOld", "new123", "new123")));

            assertEquals("Mật khẩu hiện tại không đúng", ex.getMessage());
            verify(keyCloakAuthClient, never()).updateUserPassword(anyString(), anyString());
        }

        // UTCID06 - Normal: đổi mật khẩu thành công
        @Test
        void changePassword_valid_updatesPassword() {
            User user = anAccount();
            user.setStatus(UserStatus.ACTIVE);
            user.setUsername("traveler01");
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.of(user));
            when(keyCloakAuthClient.login("traveler01", "old123")).thenReturn(tokenWith("kc-001", List.of("EXPLORER")));

            authService.changePassword("kc-001", changeRequest("old123", "new123", "new123"));

            verify(keyCloakAuthClient).updateUserPassword("kc-001", "new123");
        }
    }

    // =====================================================================
    // Function: loginGoogle
    // =====================================================================
    @Nested
    @DisplayName("loginGoogle")
    class LoginGoogleTest {

        // UTCID01 - Abnormal: không đổi được mã code lấy token
        @Test
        void loginGoogle_exchangeFails_throwsExchangeError() {
            when(keyCloakAuthClient.exchangeCode("code", "uri")).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.loginGoogle("code", "uri", "mobile"));

            assertEquals("Không thể trao đổi mã xác thực để lấy Token từ Keycloak", ex.getMessage());
        }

        // UTCID02 - Abnormal: token thiếu email
        @Test
        void loginGoogle_missingEmail_throwsMissingIdentity() {
            when(keyCloakAuthClient.exchangeCode("code", "uri"))
                    .thenReturn(socialToken("kc-001", null, List.of("EXPLORER")));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.loginGoogle("code", "uri", "mobile"));

            assertEquals("Token không hợp lệ hoặc thiếu thông tin định danh", ex.getMessage());
        }

        // UTCID03 - Abnormal: EXPLORER đăng nhập Google vào web portal bị chặn
        @Test
        void loginGoogle_explorerOnWeb_throwsForbidden() {
            when(keyCloakAuthClient.exchangeCode("code", "uri"))
                    .thenReturn(socialToken("kc-001", "a@gmail.com", List.of("EXPLORER")));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.loginGoogle("code", "uri", "web"));

            assertEquals("Bạn không có quyền truy cập", ex.getMessage());
        }

        // UTCID04 - Normal: người dùng mới -> tự tạo tài khoản EXPLORER và đăng nhập
        @Test
        void loginGoogle_newUser_autoProvisionsAndLogsIn() {
            when(keyCloakAuthClient.exchangeCode("code", "uri"))
                    .thenReturn(socialToken("kc-001", "a@gmail.com", List.of("EXPLORER")));
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.empty());
            when(levelRepository.findFirstByStatusOrderByRequiredXpAsc(LevelStatus.ACTIVE)).thenReturn(Optional.empty());

            LoginResponse response = authService.loginGoogle("code", "uri", "mobile");

            assertNotNull(response.getAccessToken());
            verify(userRepository).save(any(User.class));
        }

        // UTCID05 - Abnormal: người dùng đã tồn tại nhưng bị khóa
        @Test
        void loginGoogle_existingLockedUser_throwsLocked() {
            when(keyCloakAuthClient.exchangeCode("code", "uri"))
                    .thenReturn(socialToken("kc-001", "a@gmail.com", List.of("EXPLORER")));
            User user = anAccount();
            user.setStatus(UserStatus.INACTIVE);
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.of(user));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.loginGoogle("code", "uri", "mobile"));

            assertEquals("Tài khoản của bạn đã bị khóa hoặc ngừng hoạt động", ex.getMessage());
        }

        // UTCID06 - Normal: người dùng đã tồn tại, đang hoạt động -> đăng nhập
        @Test
        void loginGoogle_existingActiveUser_logsIn() {
            when(keyCloakAuthClient.exchangeCode("code", "uri"))
                    .thenReturn(socialToken("kc-001", "a@gmail.com", List.of("EXPLORER")));
            User user = anAccount();
            user.setStatus(UserStatus.ACTIVE);
            when(userRepository.findByKeycloakUserId("kc-001")).thenReturn(Optional.of(user));

            LoginResponse response = authService.loginGoogle("code", "uri", "mobile");

            assertNotNull(response.getAccessToken());
            verify(userRepository, never()).save(any(User.class));
        }
    }

    // =====================================================================
    // Function: loginFacebook
    // =====================================================================
    @Nested
    @DisplayName("loginFacebook")
    class LoginFacebookTest {

        // UTCID01 - Abnormal: không đổi được mã code lấy token
        @Test
        void loginFacebook_exchangeFails_throwsExchangeError() {
            when(keyCloakAuthClient.exchangeCode("code", "uri")).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.loginFacebook("code", "uri", "mobile"));

            assertEquals("Không thể trao đổi mã xác thực để lấy Token từ Keycloak", ex.getMessage());
        }

        // UTCID02 - Abnormal: token thiếu định danh (không có sub)
        @Test
        void loginFacebook_missingSub_throwsMissingIdentity() {
            String noSubPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    "{\"email\":\"a@gmail.com\",\"realm_access\":{\"roles\":[\"EXPLORER\"]}}"
                            .getBytes(StandardCharsets.UTF_8));
            KeyCloakTokenResponse token = new KeyCloakTokenResponse();
            token.setAccessToken("header." + noSubPayload + ".signature");
            when(keyCloakAuthClient.exchangeCode("code", "uri")).thenReturn(token);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.loginFacebook("code", "uri", "mobile"));

            assertEquals("Token không hợp lệ hoặc thiếu thông tin định danh", ex.getMessage());
        }

        // UTCID03 - Normal: người dùng mới không có email -> tự sinh email và tạo tài khoản
        @Test
        void loginFacebook_newUserWithoutEmail_synthesizesEmailAndCreates() {
            when(keyCloakAuthClient.exchangeCode("code", "uri"))
                    .thenReturn(socialToken("kc-fb-001", null, List.of("EXPLORER")));
            when(userRepository.findByKeycloakUserId("kc-fb-001")).thenReturn(Optional.empty());
            when(levelRepository.findFirstByStatusOrderByRequiredXpAsc(LevelStatus.ACTIVE)).thenReturn(Optional.empty());

            LoginResponse response = authService.loginFacebook("code", "uri", "mobile");

            assertNotNull(response.getAccessToken());
            assertEquals("fb_kc-fb-001@facebook.com", captureSavedUser().getEmail());
        }

        // UTCID06 - Abnormal: EXPLORER đăng nhập Facebook vào web portal bị chặn
        @Test
        void loginFacebook_explorerOnWeb_throwsForbidden() {
            when(keyCloakAuthClient.exchangeCode("code", "uri"))
                    .thenReturn(socialToken("kc-fb-001", "a@gmail.com", List.of("EXPLORER")));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.loginFacebook("code", "uri", "web"));

            assertEquals("Bạn không có quyền truy cập", ex.getMessage());
        }

        // UTCID04 - Abnormal: người dùng đã tồn tại nhưng bị khóa
        @Test
        void loginFacebook_existingLockedUser_throwsLocked() {
            when(keyCloakAuthClient.exchangeCode("code", "uri"))
                    .thenReturn(socialToken("kc-fb-001", "a@gmail.com", List.of("EXPLORER")));
            User user = anAccount();
            user.setStatus(UserStatus.INACTIVE);
            when(userRepository.findByKeycloakUserId("kc-fb-001")).thenReturn(Optional.of(user));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.loginFacebook("code", "uri", "mobile"));

            assertEquals("Tài khoản của bạn đã bị khóa hoặc ngừng hoạt động", ex.getMessage());
        }

        // UTCID05 - Normal: người dùng đã tồn tại, đang hoạt động -> đăng nhập
        @Test
        void loginFacebook_existingActiveUser_logsIn() {
            when(keyCloakAuthClient.exchangeCode("code", "uri"))
                    .thenReturn(socialToken("kc-fb-001", "a@gmail.com", List.of("EXPLORER")));
            User user = anAccount();
            user.setStatus(UserStatus.ACTIVE);
            when(userRepository.findByKeycloakUserId("kc-fb-001")).thenReturn(Optional.of(user));

            LoginResponse response = authService.loginFacebook("code", "uri", "mobile");

            assertNotNull(response.getAccessToken());
            verify(userRepository, never()).save(any(User.class));
        }
    }

    // =====================================================================
    // Function: syncSocialUser (mobile tự đổi code với Keycloak rồi gọi backend)
    // =====================================================================
    @Nested
    @DisplayName("syncSocialUser")
    class SyncSocialUserTest {

        // UTCID01 - Normal: user Facebook mới có email -> lưu đúng email và username thật
        @Test
        void syncSocialUser_newFacebookUserWithEmail_savesRealEmailAndUsername() {
            when(userRepository.findByKeycloakUserId("kc-fb-001")).thenReturn(Optional.empty());
            when(levelRepository.findFirstByStatusOrderByRequiredXpAsc(LevelStatus.ACTIVE)).thenReturn(Optional.empty());

            authService.syncSocialUser(
                    verifiedJwt("kc-fb-001", "an@gmail.com", "an@gmail.com", "Nguyễn Văn An"), "facebook");

            User saved = captureSavedUser();
            assertEquals("an@gmail.com", saved.getEmail());
            assertEquals("an@gmail.com", saved.getUsername());
            assertEquals("Nguyễn Văn An", saved.getDisplayName());
        }

        // UTCID02 - Normal: Facebook không trả email -> sinh email và username dự phòng
        @Test
        void syncSocialUser_newFacebookUserWithoutEmail_synthesizesEmailAndUsername() {
            when(userRepository.findByKeycloakUserId("kc-fb-002")).thenReturn(Optional.empty());
            when(levelRepository.findFirstByStatusOrderByRequiredXpAsc(LevelStatus.ACTIVE)).thenReturn(Optional.empty());

            authService.syncSocialUser(verifiedJwt("kc-fb-002", null, null, null), "facebook");

            User saved = captureSavedUser();
            assertEquals("fb_kc-fb-002@facebook.com", saved.getEmail());
            assertEquals("fb_kc-fb-002", saved.getUsername());
            assertEquals("Người dùng Facebook", saved.getDisplayName());
        }

        // UTCID03 - Abnormal: username mong muốn đã bị tài khoản khác chiếm -> nối hậu tố
        @Test
        void syncSocialUser_usernameTaken_appendsSuffix() {
            when(userRepository.findByKeycloakUserId("abcdef12-3456-7890-abcd-ef1234567890"))
                    .thenReturn(Optional.empty());
            when(userRepository.existsByUsername("an@gmail.com")).thenReturn(true);
            when(levelRepository.findFirstByStatusOrderByRequiredXpAsc(LevelStatus.ACTIVE)).thenReturn(Optional.empty());

            authService.syncSocialUser(
                    verifiedJwt("abcdef12-3456-7890-abcd-ef1234567890", "an@gmail.com", "an@gmail.com", "An"),
                    "facebook");

            assertEquals("an@gmail.com_abcdef", captureSavedUser().getUsername());
        }

        // UTCID04 - Normal: user đã tồn tại -> không tạo mới (Keycloak auto-link theo email)
        @Test
        void syncSocialUser_existingUser_doesNotCreateDuplicate() {
            User user = anAccount();
            user.setStatus(UserStatus.ACTIVE);
            when(userRepository.findByKeycloakUserId("kc-fb-001")).thenReturn(Optional.of(user));

            authService.syncSocialUser(verifiedJwt("kc-fb-001", "an@gmail.com", "an@gmail.com", "An"), "facebook");

            verify(userRepository, never()).save(any(User.class));
            verify(userMapper).toProfileResponse(user);
        }

        // UTCID05 - Abnormal: tài khoản bị khóa
        @Test
        void syncSocialUser_lockedUser_throwsLocked() {
            User user = anAccount();
            user.setStatus(UserStatus.INACTIVE);
            when(userRepository.findByKeycloakUserId("kc-fb-001")).thenReturn(Optional.of(user));

            BusinessException ex = assertThrows(BusinessException.class, () -> authService
                    .syncSocialUser(verifiedJwt("kc-fb-001", "an@gmail.com", "an@gmail.com", "An"), "facebook"));

            assertEquals("Tài khoản của bạn đã bị khóa hoặc ngừng hoạt động", ex.getMessage());
        }

        // UTCID06 - Abnormal: Google bắt buộc phải có email
        @Test
        void syncSocialUser_googleWithoutEmail_throwsMissingIdentity() {
            when(userRepository.findByKeycloakUserId("kc-gg-001")).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.syncSocialUser(verifiedJwt("kc-gg-001", null, "traveler01", "An"), "google"));

            assertEquals("Token không hợp lệ hoặc thiếu thông tin định danh", ex.getMessage());
            verify(userRepository, never()).save(any(User.class));
        }
    }
}
