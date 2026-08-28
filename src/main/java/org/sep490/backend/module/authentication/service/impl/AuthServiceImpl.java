package org.sep490.backend.module.authentication.service.impl;

import org.sep490.backend.module.authentication.service.AuthTokenService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.utils.SecurityUtils;
import org.sep490.backend.config.keycloak.KeyCloakAuthClient;
import org.sep490.backend.config.keycloak.KeyCloakTokenResponse;
import org.sep490.backend.common.service.TransactionCompensationService;
import org.sep490.backend.module.authentication.dto.request.*;
import org.sep490.backend.module.authentication.dto.response.LoginResponse;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.sep490.backend.module.authentication.mapper.UserMapper;
import org.sep490.backend.module.user.entity.LevelProgress;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.sep490.backend.module.user.repository.LevelProgressRepository;
import org.sep490.backend.module.user.repository.LevelRepository;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.user.entity.enumeration.LevelStatus;
import org.sep490.backend.module.authentication.service.AuthService;
import org.sep490.backend.module.user.dto.response.UserProfileResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final Set<String> WEB_ALLOWED_ROLES = Set.of("ADMIN", "CURATOR", "PARTNER");
    private static final String PROVIDER_GOOGLE = "google";
    private static final String PROVIDER_FACEBOOK = "facebook";
    private static final String FACEBOOK_USERNAME_PREFIX = "fb_";
    private static final String FACEBOOK_EMAIL_DOMAIN = "@facebook.com";
    private static final int USERNAME_MAX_LENGTH = 50;
    private static final String PLATFORM_MOBILE = "MOBILE";
    private static final Pattern RESET_TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    private final UserRepository userRepository;
    private final KeyCloakAuthClient keyCloakAuthClient;
    private final TransactionCompensationService txCompensation;
    private final UserMapper userMapper;
    private final JavaMailSender mailSender;
    private final LevelRepository levelRepository;
    private final LevelProgressRepository levelProgressRepository;
    private final AuthTokenService authTokenService;

    private static final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.frontend-url:${FRONTEND_URL:http://localhost:3000}}")
    private String frontendUrl;

    @Value("${app.mobile.reset-password-link}")
    private String mobileResetPasswordLink;

    @Value("${app.mobile.deep-link-scheme}")
    private String mobileDeepLinkScheme;

    @Override
    @Transactional
    public UserProfileResponse register(RegistrationRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Tên đăng nhập đã tồn tại");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email đã tồn tại");
        }

        String keycloakUserId;

        keycloakUserId = keyCloakAuthClient.createUser(
                request.getUsername(),
                request.getEmail(),
                request.getDisplayName(),
                request.getPassword(),
                List.of("EXPLORER"));

        txCompensation.runOnRollback(
                "Xóa Keycloak user " + keycloakUserId,
                () -> keyCloakAuthClient.safeDeleteUser(keycloakUserId));

        try {
            sendVerificationOtp(request.getEmail());
            User user = buildCustomer(request, keycloakUserId);
            user.setStatus(UserStatus.PENDING);
            user = userRepository.save(user);
            createInitialLevelProgress(user);
            keyCloakAuthClient.updateUserAttribute(keycloakUserId, "internal_id", String.valueOf(user.getUserId()));
            return userMapper.toProfileResponse(user);
        } catch (Exception e) {
            rollbackKeycloakUser(keycloakUserId);
            throw new BusinessException("Đăng ký tài khoản thất bại: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void verifyEmailWithOtp(VerifyOtpRequest request) {
        String email = request.getEmail().trim();
        String userOtp = request.getOtpCode().trim();

        if (authTokenService.isAttemptExceeded(email)) {
            throw new BusinessException("Bạn đã nhập sai quá nhiều lần. Vui lòng yêu cầu mã mới sau ít phút.");
        }

        String storedOtp = authTokenService.findOtp(email)
                .orElseThrow(() -> new BusinessException(
                        "Mã OTP không tồn tại hoặc đã hết hiệu lực. Vui lòng yêu cầu gửi lại"));

        if (!storedOtp.equals(userOtp)) {
            throw new BusinessException("Mã OTP không chính xác");
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BusinessException("Không tìm thấy người dùng khớp với email này"));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        authTokenService.deleteOtp(email);
    }

    @Override
    @Transactional
    public void resendOtp(SendOtpRequest request) {
        String email = request.getEmail().trim();

        long secondsLeft = authTokenService.acquireResendSlot(email);
        if (secondsLeft > 0) {
            throw new BusinessException(
                    "Vui lòng đợi thêm " + secondsLeft + " giây nữa để yêu cầu gửi lại mã OTP.");
        }
        sendVerificationOtp(email);
    }

    @Override
    public LoginResponse login(LoginRequest request, String clientType) {
        if (request.getUsername() == null || request.getPassword() == null) {
            throw new BusinessException("Thiếu thông tin đăng nhập");
        }

        KeyCloakTokenResponse tokenResponse;
        try {
            tokenResponse = keyCloakAuthClient.login(request.getUsername(), request.getPassword());
        } catch (BusinessException e) {
            throw new BusinessException("Tên đăng nhập hoặc mật khẩu không chính xác");
        }

        if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
            throw new BusinessException("Tên đăng nhập hoặc mật khẩu không chính xác");
        }

        Map<String, Object> payload = decodeJwtPayload(tokenResponse.getAccessToken());
        String keycloakUserId = (String) payload.get("sub");

        Map<String, Object> realmAccess = (Map<String, Object>) payload.get("realm_access");
        List<String> roles = realmAccess != null ? (List<String>) realmAccess.get("roles") : List.of();
        log.debug("User '{}' has realm roles: {}", request.getUsername(), roles);

        boolean hasAllowedRole = roles.stream().anyMatch(role -> "EXPLORER".equals(role) || "ADMIN".equals(role)
                || "CURATOR".equals(role) || "PARTNER".equals(role));
        if (!hasAllowedRole) {
            log.warn("Login denied for user '{}': no allowed role found in realm_access.roles = {}",
                    request.getUsername(), roles);
            throw new BusinessException("Tài khoản không có quyền truy cập");
        }

        enforceWebRoleAccess(roles, clientType, tokenResponse);

        User user = userRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new BusinessException("Tên đăng nhập hoặc mật khẩu không chính xác"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            String errorMessage = roles.contains("EXPLORER") || roles.contains("CURATOR") || roles.contains("PARTNER")
                    ? "Tài khoản của bạn đã bị khóa hoặc ngừng hoạt động"
                    : "Tài khoản admin của bạn đã bị khóa hoặc ngừng hoạt động";
            throw new BusinessException(errorMessage);
        }

        return buildLoginResponse(tokenResponse);
    }

    @Override
    public LoginResponse refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new BusinessException("Không tìm thấy refresh token. Vui lòng đăng nhập lại");
        }
        KeyCloakTokenResponse tokenResponse = keyCloakAuthClient.refreshToken(refreshToken);
        if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
            throw new BusinessException("Refresh token không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại");
        }
        return buildLoginResponse(tokenResponse);
    }

    @Override
    public void logout(String refreshToken) {
        keyCloakAuthClient.logout(refreshToken);

        SecurityUtils.getCurrentJwt().ifPresent(jwt -> {
            String jti = jwt.getId();
            Instant expiresAt = jwt.getExpiresAt();
            if (jti == null || expiresAt == null) {
                return;
            }
            try {
                authTokenService.denyToken(jti, Duration.between(Instant.now(), expiresAt));
            } catch (Exception e) {
                log.warn("Không ghi được denylist cho jti {}: {}", jti, e.getMessage());
            }
        });
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.getEmail().trim())
                .orElseThrow(() -> new BusinessException("Vui lòng kiểm tra lại email"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Tài khoản của bạn chưa được kích hoạt hoặc đã bị khóa");
        }

        String token = UUID.randomUUID().toString();
        authTokenService.savePasswordResetToken(token, user.getUserId());

        String resetUrl = buildResetPasswordLink(request.getPlatform(), token);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(user.getEmail());
            helper.setSubject("[CULTURE QUEST LITE] YÊU CẦU ĐẶT LẠI MẬT KHẨU");

            ClassPathResource resource = new ClassPathResource("templates/reset-password-email.html");
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            content = content.replace("{{RESET_LINK}}", resetUrl);

            helper.setText(content, true);
            mailSender.send(message);
            log.info("Đã gửi email đặt lại mật khẩu tới: {}", user.getEmail());
        } catch (MessagingException | IOException e) {
            log.error("Lỗi khi gửi email đặt lại mật khẩu", e);
            throw new BusinessException("Không thể gửi email lúc này. Vui lòng thử lại sau!");
        }
    }

    private String buildResetPasswordLink(String platform, String token) {
        if (platform != null && PLATFORM_MOBILE.equalsIgnoreCase(platform.trim())) {
            return mobileResetPasswordLink + "?token=" + token;
        }

        return frontendUrl + "/reset-password?token=" + token;
    }

    @Override
    public String buildResetPasswordRedirectPage(String token) {
        if (token == null || !RESET_TOKEN_PATTERN.matcher(token).matches()) {
            throw new BusinessException("Liên kết đổi mật khẩu không hợp lệ hoặc đã hết hạn");
        }

        try {
            ClassPathResource resource = new ClassPathResource("templates/reset-password-redirect.html");
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);

            return content
                    .replace("{{DEEP_LINK}}", mobileDeepLinkScheme + "://reset-password?token=" + token)
                    .replace("{{WEB_LINK}}", frontendUrl + "/reset-password?token=" + token);
        } catch (IOException e) {
            log.error("Không đọc được template reset-password-redirect.html", e);
            throw new BusinessException("Không mở được liên kết đổi mật khẩu. Vui lòng thử lại sau!");
        }
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.isPasswordMatch()) {
            throw new BusinessException("Mật khẩu không trùng khớp");
        }

        Long userId = authTokenService.findUserIdByResetToken(request.getToken())
                .orElseThrow(() -> new BusinessException(
                        "Liên kết đổi mật khẩu không hợp lệ hoặc đã hết hạn"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));

        keyCloakAuthClient.resetUserPassword(user.getKeycloakUserId(), request.getNewPassword());
        userRepository.save(user);
        authTokenService.deletePasswordResetToken(request.getToken(), userId);
    }

    @Override
    public void changePassword(String keycloakUserId, ChangePasswordRequest request) {
        if (!request.isPasswordMatch()) {
            throw new BusinessException("Mật khẩu mới và xác nhận mật khẩu không khớp");
        }

        User user = userRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new BusinessException("Người dùng không tồn tại"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Tài khoản đã bị khóa hoặc ngừng hoạt động");
        }

        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new BusinessException("Mật khẩu mới không được trùng với mật khẩu hiện tại");
        }

        try {
            keyCloakAuthClient.login(user.getUsername(), request.getOldPassword());
        } catch (BusinessException e) {
            throw new BusinessException("Mật khẩu hiện tại không đúng");
        }

        keyCloakAuthClient.updateUserPassword(keycloakUserId, request.getNewPassword());
    }

    @Override
    @Transactional
    public LoginResponse loginGoogle(String code, String redirectUri, String clientType) {
        KeyCloakTokenResponse tokenResponse = keyCloakAuthClient.exchangeCode(code, redirectUri);
        if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
            throw new BusinessException("Không thể trao đổi mã xác thực để lấy Token từ Keycloak");
        }

        String accessToken = tokenResponse.getAccessToken();
        Map<String, Object> payload = decodeJwtPayload(accessToken);
        String keycloakUserId = (String) payload.get("sub");
        String email = (String) payload.get("email");
        String preferredUsername = (String) payload.get("preferred_username");
        String displayName = (String) payload.get("name");

        if (keycloakUserId == null || email == null) {
            throw new BusinessException("Token không hợp lệ hoặc thiếu thông tin định danh");
        }

        Map<String, Object> realmAccess = (Map<String, Object>) payload.get("realm_access");
        List<String> roles = realmAccess != null ? (List<String>) realmAccess.get("roles") : List.of();
        enforceWebRoleAccess(roles, clientType, tokenResponse);

        findOrCreateSocialUser(keycloakUserId, email, preferredUsername, displayName,
                (String) payload.get("picture"), PROVIDER_GOOGLE);

        return buildLoginResponse(tokenResponse);
    }

    @Override
    @Transactional
    public LoginResponse loginFacebook(String code, String redirectUri, String clientType) {
        KeyCloakTokenResponse tokenResponse = keyCloakAuthClient.exchangeCode(code, redirectUri);
        if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
            throw new BusinessException("Không thể trao đổi mã xác thực để lấy Token từ Keycloak");
        }

        String accessToken = tokenResponse.getAccessToken();
        Map<String, Object> payload = decodeJwtPayload(accessToken);
        String keycloakUserId = (String) payload.get("sub");

        if (keycloakUserId == null) {
            throw new BusinessException("Token không hợp lệ hoặc thiếu thông tin định danh");
        }

        Map<String, Object> realmAccess = (Map<String, Object>) payload.get("realm_access");
        List<String> roles = realmAccess != null ? (List<String>) realmAccess.get("roles") : List.of();
        enforceWebRoleAccess(roles, clientType, tokenResponse);

        findOrCreateSocialUser(keycloakUserId,
                (String) payload.get("email"),
                (String) payload.get("preferred_username"),
                (String) payload.get("name"),
                (String) payload.get("picture"),
                PROVIDER_FACEBOOK);

        return buildLoginResponse(tokenResponse);
    }

    @Override
    @Transactional
    public UserProfileResponse syncSocialUser(Jwt jwt, String provider) {
        User user = findOrCreateSocialUser(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("picture"),
                provider);
        return userMapper.toProfileResponse(user);
    }

    private User findOrCreateSocialUser(String keycloakUserId, String email, String preferredUsername,
            String displayName, String avatarUrl, String provider) {
        if (isBlank(keycloakUserId)) {
            throw new BusinessException("Token không hợp lệ hoặc thiếu thông tin định danh");
        }

        Optional<User> userOpt = userRepository.findByKeycloakUserId(keycloakUserId);
        if (userOpt.isPresent()) {
            User existingUser = userOpt.get();
            if (existingUser.getStatus() != UserStatus.ACTIVE) {
                throw new BusinessException("Tài khoản của bạn đã bị khóa hoặc ngừng hoạt động");
            }
            return existingUser;
        }

        boolean isFacebook = PROVIDER_FACEBOOK.equalsIgnoreCase(provider);
        String resolvedEmail = email;
        if (isBlank(resolvedEmail)) {
            if (!isFacebook) {
                throw new BusinessException("Token không hợp lệ hoặc thiếu thông tin định danh");
            }
            resolvedEmail = FACEBOOK_USERNAME_PREFIX + keycloakUserId + FACEBOOK_EMAIL_DOMAIN;
        }

        String desiredUsername = !isBlank(preferredUsername)
                ? preferredUsername
                : (isFacebook ? FACEBOOK_USERNAME_PREFIX + keycloakUserId : resolvedEmail);
        String resolvedDisplayName = firstNonBlank(displayName, preferredUsername,
                isFacebook ? "Người dùng Facebook" : resolvedEmail);

        try {
            keyCloakAuthClient.updateUserRoles(keycloakUserId, List.of("EXPLORER"));
        } catch (Exception e) {
            log.error("Lỗi khi tự động gán role EXPLORER trong Keycloak: {}", e.getMessage());
        }

        User newUser = User.builder()
                .keycloakUserId(keycloakUserId)
                .username(resolveAvailableUsername(desiredUsername, keycloakUserId))
                .email(resolvedEmail)
                .displayName(resolvedDisplayName)
                .avatarUrl(avatarUrl)
                .status(UserStatus.ACTIVE)
                .totalXp(0)
                .totalPoints(0)
                .autoPlayAudio(true)
                .isPremium(false)
                .role(UserRole.EXPLORER)
                .build();

        levelRepository.findFirstByStatusOrderByRequiredXpAsc(LevelStatus.ACTIVE)
                .ifPresent(newUser::setLevel);

        userRepository.save(newUser);
        createInitialLevelProgress(newUser);
        return newUser;
    }

    private String resolveAvailableUsername(String desiredUsername, String keycloakUserId) {
        String base = truncate(desiredUsername, USERNAME_MAX_LENGTH);
        if (!userRepository.existsByUsername(base)) {
            return base;
        }

        String compactId = keycloakUserId.replace("-", "");
        for (int suffixLength = 6; suffixLength <= compactId.length(); suffixLength += 6) {
            String suffix = "_" + compactId.substring(0, suffixLength);
            String candidate = truncate(base, USERNAME_MAX_LENGTH - suffix.length()) + suffix;
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException("Không thể tạo tên đăng nhập cho tài khoản mạng xã hội");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void enforceWebRoleAccess(List<String> roles, String clientType, KeyCloakTokenResponse tokenResponse) {
        if (!isWebClient(clientType) || roles.stream().anyMatch(WEB_ALLOWED_ROLES::contains)) {
            return;
        }
        try {
            keyCloakAuthClient.logout(tokenResponse.getRefreshToken());
        } catch (Exception e) {
            log.warn("Không thể logout Keycloak session khi từ chối truy cập: {}", e.getMessage());
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập");
    }

    private boolean isWebClient(String clientType) {
        return !"mobile".equalsIgnoreCase(clientType);
    }

    private LoginResponse buildLoginResponse(KeyCloakTokenResponse tokenResponse) {
        return LoginResponse.builder()
                .accessToken(tokenResponse.getAccessToken())
                .tokenType(tokenResponse.getTokenType())
                .expiresIn(tokenResponse.getExpiresIn())
                .refreshToken(tokenResponse.getRefreshToken())
                .refreshExpiresIn(tokenResponse.getRefreshExpiresIn())
                .build();
    }

    private Map<String, Object> decodeJwtPayload(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new BusinessException("Token Keycloak không hợp lệ");
            }
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            return new ObjectMapper().readValue(payloadJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new BusinessException("Xác thực token thất bại");
        }
    }

    private void sendVerificationOtp(String rawEmail) {
        String email = rawEmail.trim();
        String otpCode = String.format("%06d", secureRandom.nextInt(1000000));

        authTokenService.saveOtp(email, otpCode);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(email);
            helper.setSubject("[CULTURE QUEST LITE] MÃ OTP XÁC THỰC EMAIL");

            ClassPathResource resource = new ClassPathResource("templates/otp-email.html");
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            content = content.replace("{{OTP_CODE}}", otpCode);

            helper.setText(content, true);
            mailSender.send(message);
        } catch (IOException | MessagingException e) {
            log.error("Lỗi khi gửi email xác thực OTP", e);
            throw new BusinessException("Không thể gửi email chứa mã OTP lúc này. Vui lòng thử lại sau");
        }
    }

    private User buildCustomer(RegistrationRequest request, String keycloakUserId) {
        User user = userMapper.toEntity(request);
        user.setTotalXp(0);
        user.setKeycloakUserId(keycloakUserId);
        user.setRole(UserRole.EXPLORER);
        levelRepository.findFirstByStatusOrderByRequiredXpAsc(LevelStatus.ACTIVE)
                .ifPresent(user::setLevel);
        return user;
    }

    private void rollbackKeycloakUser(String keycloakUserId) {
        keyCloakAuthClient.safeDeleteUser(keycloakUserId);
    }

    private void createInitialLevelProgress(User user) {
        if (user.getLevel() != null) {
            LevelProgress lp = LevelProgress.builder()
                    .user(user)
                    .level(user.getLevel())
                    .xpAtUnlock(0)
                    .unlockedAt(LocalDateTime.now())
                    .build();
            levelProgressRepository.save(lp);
        }
    }
}
