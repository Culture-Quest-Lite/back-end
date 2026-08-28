package org.sep490.backend.module.authorization.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.sep490.backend.common.utils.SecurityUtils;
import org.sep490.backend.module.authorization.service.OwnershipService;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.ReviewRepository;
import org.sep490.backend.module.content.repository.RouteRepository;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.exploration.repository.SavedRouteRepository;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupRole;
import org.sep490.backend.module.groupquest.repository.GroupParticipantRepository;
import org.sep490.backend.module.planner.repository.UserPlanRepository;
import org.sep490.backend.module.social.repository.PostRepository;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@Service("perm")
@RequiredArgsConstructor
public class OwnershipServiceImpl implements OwnershipService {

    private final UserService userService;
    private final PostRepository postRepository;
    private final ReviewRepository reviewRepository;
    private final RouteRepository routeRepository;
    private final HotspotRepository hotspotRepository;
    private final StoryRepository storyRepository;
    private final UserPlanRepository userPlanRepository;
    private final SavedRouteRepository savedRouteRepository;
    private final GroupParticipantRepository groupParticipantRepository;

    private Map<String, Function<Long, Optional<Long>>> resolvers;

    @PostConstruct
    void init() {
        resolvers = Map.of(
                "POST", postRepository::findOwnerId,
                "REVIEW", reviewRepository::findOwnerId,
                "ROUTE", routeRepository::findOwnerId,
                "HOTSPOT", hotspotRepository::findOwnerId,
                "STORY", storyRepository::findOwnerId,
                "PLAN", userPlanRepository::findOwnerId,
                "SAVED_ROUTE", savedRouteRepository::findOwnerId
        );
    }


    @Override
    @Transactional(readOnly = true)
    public boolean isOwner(Long resourceId, String type) {
        if (resourceId == null) return false;

        Function<Long, Optional<Long>> resolver = resolvers.get(type);
        if (resolver == null) {
            throw new IllegalArgumentException("Loại tài nguyên chưa khai báo: " + type);
        }

        Long currentUserId = userService.getCurrentUser().getUserId();
        return resolver.apply(resourceId)
                .map(ownerId -> Objects.equals(ownerId, currentUserId))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOwnerOrHasPerm(Long resourceId, String type, String permissionCode) {
        return SecurityUtils.hasPermission(permissionCode) || isOwner(resourceId, type);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isGroupLeader(Long groupId) {
        Long userId = userService.getCurrentUser().getUserId();
        return Boolean.TRUE.equals(groupParticipantRepository
                .existsByGroup_GroupIdAndUser_UserIdAndRole(groupId, userId, GroupRole.LEADER));
    }
}
