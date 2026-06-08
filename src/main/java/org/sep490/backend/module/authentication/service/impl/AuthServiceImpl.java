package org.sep490.backend.module.authentication.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.config.keycloak.KeyCloakAuthClient;
import org.sep490.backend.config.keycloak.KeyCloakTokenResponse;
import org.sep490.backend.module.authentication.dto.request.*;
import org.sep490.backend.module.authentication.dto.response.LoginResponse;
import org.sep490.backend.module.authentication.dto.response.RegistrationResponse;
import org.sep490.backend.module.authentication.entity.EmailOtp;
import org.sep490.backend.module.authentication.entity.PasswordResetToken;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.sep490.backend.module.authentication.mapper.UserMapper;
import org.sep490.backend.module.authentication.repository.EmailOtpRepository;
import org.sep490.backend.module.authentication.repository.LevelRepository;
import org.sep490.backend.module.authentication.repository.PasswordResetTokenRepository;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.authentication.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final KeyCloakAuthClient keyCloakAuthClient;
    private final UserMapper userMapper;
    private final JavaMailSender mailSender;
    private final EmailOtpRepository emailOtpRepository;
    private final LevelRepository levelRepository;
    private final PasswordResetTokenRepository tokenRepository;

    @Value("${app.frontend-url:${FRONTEND_URL:http://localhost:3000}}")
    private String frontendUrl;

    @Override
    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
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
        try {
            sendVerificationOtp(request.getEmail());
            User user = buildCustomer(request, keycloakUserId);
            user.setStatus(UserStatus.PENDING);
            user = userRepository.save(user);
            return userMapper.toResponse(user);
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

        EmailOtp emailOtp = emailOtpRepository.findFirstByEmailOrderByExpiryDateDesc(email)
                .orElseThrow(() -> new BusinessException("Không tìm thấy yêu cầu xác thực OTP"));

        if (!emailOtp.getOtpCode().equals(userOtp)) {
            throw new BusinessException("Mã OTP không chính xác");
        }

        if (emailOtp.isExpired()) {
            emailOtpRepository.delete(emailOtp);
            throw new BusinessException("Mã OTP đã hết hiệu lực. Vui lòng thử lại sau");
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BusinessException("Không tìm thấy người dùng khớp với email này"));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        emailOtpRepository.deleteByEmail(email);
    }

    @Override
    @Transactional
    public void resendOtp(SendOtpRequest request) {
        String email = request.getEmail().trim();

        Optional<EmailOtp> existingOtp = emailOtpRepository.findFirstByEmailOrderByExpiryDateDesc(email);
        if (existingOtp.isPresent()) {
            EmailOtp emailOtp = existingOtp.get();
            long secondsSinceLastOtp = Duration.between(emailOtp.getCreatedAt(), LocalDateTime.now()).getSeconds();
            if (secondsSinceLastOtp < 30) {
                long secondsLeft = 30 - secondsSinceLastOtp;
                throw new BusinessException(
                        "Vui lòng đợi thêm " + secondsLeft + " giây nữa để yêu cầu gửi lại mã OTP.");
            }
        }
        sendVerificationOtp(email);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
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
            throw new BusinessException("Tài khoản không có quyền truy cập cửa hàng này");
        }

        User user = userRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new BusinessException("Tên đăng nhập hoặc mật khẩu không chính xác"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            String errorMessage = roles.contains("EXPLORER") || roles.contains("CURATOR") || roles.contains("PARTNER")
                    ? "Tài khoản của bạn đã bị khóa hoặc ngừng hoạt động"
                    : "Tài khoản admin của bạn đã bị khóa hoặc ngừng hoạt động";
            throw new BusinessException(errorMessage);
        }

        return LoginResponse.builder()
                .accessToken(tokenResponse.getAccessToken())
                .refreshToken(tokenResponse.getRefreshToken())
                .tokenType(tokenResponse.getTokenType())
                .expiresIn(tokenResponse.getExpiresIn())
                .refreshExpiresIn(tokenResponse.getRefreshExpiresIn())
                .build();
    }

    @Override
    public void logout(LogoutRequest request) {
        keyCloakAuthClient.logout(request.getRefreshToken());
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.getEmail().trim())
                .orElseThrow(() -> new BusinessException("Không tìm thấy email"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Tài khoản của bạn chưa được kích hoạt hoặc đã bị khóa");
        }

        tokenRepository.deleteByUser(user);
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken(token, user);
        tokenRepository.save(resetToken);

        String resetUrl = frontendUrl + "/reset-password?token=" + token;

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

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.isPasswordMatch()) {
            throw new BusinessException("Mật khẩu không trùng khớp");
        }

        PasswordResetToken resetToken = tokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new BusinessException("Liên kết đổi mật khẩu không hợp lệ hoặc đã hết hạn"));

        if (resetToken.isExpired()) {
            tokenRepository.delete(resetToken);
            throw new BusinessException("Liên kết đổi mật khẩu đã hết hạn, vui lòng yêu cầu gửi lại email mới");
        }

        User user = resetToken.getUser();
        keyCloakAuthClient.resetUserPassword(user.getKeycloakUserId(), request.getNewPassword());
        userRepository.save(user);
        tokenRepository.delete(resetToken);
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
    public LoginResponse loginGoogle(String code, String redirectUri) {
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
        Optional<User> userOpt = userRepository.findByKeycloakUserId(keycloakUserId);
        if (userOpt.isEmpty()) {
            try {
                keyCloakAuthClient.updateUserRoles(keycloakUserId, List.of("EXPLORER"));
            } catch (Exception e) {
                log.error("Lỗi khi tự động gán role EXPLORER trong Keycloak: {}", e.getMessage());
            }

            User newUser = User.builder()
                    .keycloakUserId(keycloakUserId)
                    .username(preferredUsername != null ? preferredUsername : email)
                    .email(email)
                    .displayName(displayName != null ? displayName : preferredUsername)
                    .status(UserStatus.ACTIVE)
                    .totalXp(0)
                    .totalPoints(0)
                    .autoPlayAudio(true)
                    .isPremium(false)
                    .build();

            levelRepository.findFirstByOrderByRequiredXpAsc()
                    .ifPresent(newUser::setLevel);

            userRepository.save(newUser);
        } else {
            User existingUser = userOpt.get();
            if (existingUser.getStatus() != UserStatus.ACTIVE) {
                throw new BusinessException("Tài khoản của bạn đã bị khóa hoặc ngừng hoạt động");
            }
        }

        return LoginResponse.builder()
                .accessToken(tokenResponse.getAccessToken())
                .refreshToken(tokenResponse.getRefreshToken())
                .tokenType(tokenResponse.getTokenType())
                .expiresIn(tokenResponse.getExpiresIn())
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

    private void sendVerificationOtp(String email) {
        String otpCode = String.format("%06d", new Random().nextInt(1000000));
        emailOtpRepository.deleteByEmail(email);
        EmailOtp emailOtp = new EmailOtp(email, otpCode, 2);
        emailOtpRepository.save(emailOtp);

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
        levelRepository.findFirstByOrderByRequiredXpAsc()
                .ifPresent(user::setLevel);
        return user;
    }

    private void rollbackKeycloakUser(String keycloakUserId) {
        try {
            keyCloakAuthClient.deleteUser(keycloakUserId);
            log.info("Đã rollback Keycloak user: {}", keycloakUserId);
        } catch (Exception e) {
            log.error("Không thể rollback Keycloak user: {}", keycloakUserId, e);
        }
    }
}
