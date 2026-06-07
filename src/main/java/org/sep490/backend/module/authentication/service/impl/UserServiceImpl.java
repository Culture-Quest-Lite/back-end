package org.sep490.backend.module.authentication.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.config.keycloak.KeyCloakAuthClient;
import org.sep490.backend.module.authentication.dto.request.RegistrationRequest;
import org.sep490.backend.module.authentication.dto.response.RegistrationResponse;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.sep490.backend.module.authentication.mapper.UserMapper;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.authentication.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final KeyCloakAuthClient keyCloakAuthClient;
    private final UserMapper userMapper;

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
            User user = buildCustomer(request, keycloakUserId);
            user = userRepository.save(user);
            return userMapper.toResponse(user);
        } catch (Exception e) {
            rollbackKeycloakUser(keycloakUserId);
            throw new BusinessException("Đăng ký tài khoản thất bại: " + e.getMessage());
        }
    }

    private User buildCustomer(RegistrationRequest request, String keycloakUserId) {
        User user = userMapper.toEntity(request);
        user.setTotalXp(0);
        user.setKeycloakUserId(keycloakUserId);
        user.setStatus(UserStatus.ACTIVE);
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
