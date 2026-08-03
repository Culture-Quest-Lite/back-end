package org.sep490.backend.module.content.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.utils.SecurityUtils;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.exploration.repository.UserHotspotProgressRepository;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Gán isCheckIn cho các hotspot response.
 * Dùng một truy vấn gộp cho cả danh sách để tránh N+1.
 * Khi chưa đăng nhập thì mọi hotspot đều là false.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CheckInStatusApplier {

    UserHotspotProgressRepository userHotspotProgressRepository;
    UserService userService;

    public HotspotResponse apply(HotspotResponse response) {
        apply(List.of(response));
        return response;
    }

    public void apply(List<HotspotResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }

        if (SecurityUtils.getCurrentUserKeyCloakId().isEmpty()) {
            responses.forEach(response -> response.setIsCheckIn(false));
            return;
        }

        User user = userService.getCurrentUser();

        List<Long> hotspotIds = responses.stream()
                .map(HotspotResponse::getHotspotId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Set<Long> checkedInIds = hotspotIds.isEmpty()
                ? Set.of()
                : Set.copyOf(userHotspotProgressRepository
                        .findCheckedInHotspotIds(user.getUserId(), hotspotIds));

        responses.forEach(response ->
                response.setIsCheckIn(checkedInIds.contains(response.getHotspotId())));
    }
}
