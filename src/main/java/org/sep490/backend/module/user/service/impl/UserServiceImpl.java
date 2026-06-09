package org.sep490.backend.module.user.service.impl;

import lombok.RequiredArgsConstructor;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.sep490.backend.module.authentication.mapper.UserMapper;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.user.dto.request.UpdateProfileRequest;
import org.sep490.backend.module.user.dto.response.UserProfileResponse;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(String keycloakUserId) {
        User user = userRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Tài khoản của bạn chưa được kích hoạt hoặc đã bị khóa");
        }

        return userMapper.toProfileResponse(user);
    }

    @Override
    public UserProfileResponse getProfile(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Tài khoản người dùng này hiện đang bị khóa hoặc chưa được kích hoạt");
        }
        return userMapper.toProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateMyProfile(String keycloakUserId, UpdateProfileRequest request) {
        User user = userRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Tài khoản của bạn không ở trạng thái hoạt động để cập nhật");
        }
        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName().trim());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
        }
        if (request.getAutoPlayAudio() != null) {
            user.setAutoPlayAudio(request.getAutoPlayAudio());
        }

        user = userRepository.save(user);
        return userMapper.toProfileResponse(user);
    }
}
