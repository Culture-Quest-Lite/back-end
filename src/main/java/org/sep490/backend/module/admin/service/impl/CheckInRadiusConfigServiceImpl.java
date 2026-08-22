package org.sep490.backend.module.admin.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.admin.dto.request.CheckInRadiusConfigRequest;
import org.sep490.backend.module.admin.dto.response.CheckInRadiusConfigResponse;
import org.sep490.backend.module.admin.entity.CheckInRadiusConfig;
import org.sep490.backend.module.admin.repository.CheckInRadiusConfigRepository;
import org.sep490.backend.module.admin.service.CheckInRadiusConfigService;
import org.sep490.backend.module.exploration.service.impl.CheckInPolicy;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CheckInRadiusConfigServiceImpl implements CheckInRadiusConfigService {

    CheckInRadiusConfigRepository checkInRadiusConfigRepository;
    UserService userService;

    @Override
    @Transactional
    public CheckInRadiusConfig getCurrent() {
        return checkInRadiusConfigRepository.findCurrent()
                .orElseGet(() -> checkInRadiusConfigRepository.save(CheckInRadiusConfig.builder()
                        .minRadius(CheckInPolicy.MIN_RADIUS_METERS)
                        .maxRadius(CheckInPolicy.MAX_RADIUS_METERS)
                        .defaultRadius(CheckInPolicy.DEFAULT_RADIUS_METERS)
                        .build()));
    }

    @Override
    @Transactional
    public CheckInRadiusConfigResponse getConfig() {
        return toResponse(getCurrent());
    }

    @Override
    @Transactional
    public CheckInRadiusConfigResponse updateConfig(CheckInRadiusConfigRequest request) {
        if (request.getMinRadius() > request.getMaxRadius()) {
            throw new BusinessException("Bán kính tối thiểu không được lớn hơn bán kính tối đa");
        }

        if (request.getDefaultRadius() < request.getMinRadius()
                || request.getDefaultRadius() > request.getMaxRadius()) {
            throw new BusinessException("Bán kính mặc định phải nằm trong khoảng từ "
                    + request.getMinRadius() + "m đến " + request.getMaxRadius() + "m");
        }

        CheckInRadiusConfig config = getCurrent();
        config.setMinRadius(request.getMinRadius());
        config.setMaxRadius(request.getMaxRadius());
        config.setDefaultRadius(request.getDefaultRadius());
        config.setUpdatedBy(currentUsername());

        return toResponse(checkInRadiusConfigRepository.save(config));
    }

    @Override
    @Transactional
    public int resolveRadius(Integer requestedRadius) {
        CheckInRadiusConfig config = getCurrent();

        if (requestedRadius == null) {
            return config.getDefaultRadius();
        }

        if (requestedRadius < config.getMinRadius() || requestedRadius > config.getMaxRadius()) {
            throw new BusinessException("Bán kính check-in phải nằm trong khoảng từ "
                    + config.getMinRadius() + "m đến " + config.getMaxRadius() + "m");
        }

        return requestedRadius;
    }

    @Override
    @Transactional
    public int getDefaultRadius() {
        return getCurrent().getDefaultRadius();
    }

    private String currentUsername() {
        try {
            return userService.getCurrentUser().getUsername();
        } catch (Exception e) {
            // Cấu hình vẫn phải lưu được kể cả khi không xác định được người sửa
            return null;
        }
    }

    private CheckInRadiusConfigResponse toResponse(CheckInRadiusConfig config) {
        return CheckInRadiusConfigResponse.builder()
                .minRadius(config.getMinRadius())
                .maxRadius(config.getMaxRadius())
                .defaultRadius(config.getDefaultRadius())
                .updatedAt(config.getUpdatedAt())
                .updatedBy(config.getUpdatedBy())
                .build();
    }
}
