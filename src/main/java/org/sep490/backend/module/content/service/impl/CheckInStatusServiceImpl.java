package org.sep490.backend.module.content.service.impl;

import org.sep490.backend.module.content.service.inter.CheckInStatusService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.common.utils.SecurityUtils;
import org.sep490.backend.config.redis.CacheNames;
import org.sep490.backend.config.redis.RedisCircuitBreaker;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.exploration.repository.UserHotspotProgressRepository;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CheckInStatusServiceImpl implements CheckInStatusService {

    static Duration TTL = Duration.ofHours(1);

    UserHotspotProgressRepository userHotspotProgressRepository;
    UserService userService;
    RedisTemplate<String, Object> redisTemplate;
    RedisCircuitBreaker circuitBreaker;

    @Override
    public HotspotResponse apply(HotspotResponse response) {
        apply(List.of(response));
        return response;
    }

    @Override
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
                : loadCheckedInIds(user.getUserId(), hotspotIds);

        responses.forEach(response ->
                response.setIsCheckIn(checkedInIds.contains(response.getHotspotId())));
    }


    private Set<Long> loadCheckedInIds(Long userId, List<Long> hotspotIds) {
        String key = CacheNames.KEY_CHECKIN_USER + userId;

        Set<Object> cached = circuitBreaker.read("checkin.members",
                () -> redisTemplate.opsForSet().members(key), null);
        if (cached != null && !cached.isEmpty()) {
            return cached.stream()
                    .map(o -> Long.valueOf(o.toString()))
                    .collect(Collectors.toSet());
        }

        Set<Long> fromDb = new HashSet<>(
                userHotspotProgressRepository.findAllCheckedInHotspotIds(userId));

        if (!fromDb.isEmpty()) {
            circuitBreaker.write("checkin.add", () -> {
                redisTemplate.opsForSet().add(key,
                        fromDb.stream().map(String::valueOf).toArray());
                redisTemplate.expire(key, TTL);
            });
        }
        return fromDb;
    }

    @Override
    public void addCheckedIn(Long userId, Long hotspotId) {
        if (userId == null || hotspotId == null) {
            return;
        }
        String key = CacheNames.KEY_CHECKIN_USER + userId;
        circuitBreaker.write("checkin.update", () -> {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                redisTemplate.opsForSet().add(key, String.valueOf(hotspotId));
                redisTemplate.expire(key, TTL);
            }
        });
    }
}
