package org.sep490.backend.module.authentication.service.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.config.keycloak.KeyCloakAuthClient;
import org.sep490.backend.module.authentication.dto.request.RegistrationRequest;
import org.sep490.backend.module.authentication.dto.request.SendOtpRequest;
import org.sep490.backend.module.authentication.dto.request.VerifyOtpRequest;
import org.sep490.backend.module.authentication.dto.response.RegistrationResponse;
import org.sep490.backend.module.authentication.entity.EmailOtp;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.sep490.backend.module.authentication.mapper.UserMapper;
import org.sep490.backend.module.authentication.repository.EmailOtpRepository;
import org.sep490.backend.module.authentication.entity.Level;
import org.sep490.backend.module.authentication.repository.LevelRepository;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.authentication.service.AuthService;
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
import java.util.List;
import java.util.Optional;
import java.util.Random;

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
                throw new BusinessException("Vui lòng đợi thêm " + secondsLeft + " giây nữa để yêu cầu gửi lại mã OTP.");
            }
        }
        sendVerificationOtp(email);
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
