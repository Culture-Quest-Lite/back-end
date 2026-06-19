package org.sep490.backend.module.exploration.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.exploration.dto.response.UserRouteProgressDetailResponse;
import org.sep490.backend.module.exploration.dto.response.UserRouteProgressResponse;
import org.sep490.backend.module.exploration.entity.UserRouteProgress;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface UserRouteProgressMapper {

    @Mapping(target = "totalStops", source = "route.totalStops")
    @Mapping(target = "status", constant = "IN_PROGRESS")
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "startedAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    UserRouteProgress toEntity(Route route, User user, Integer completedStops, Double progressPercentage);

    @Mapping(target = "routeId", source = "route.routeId")
    UserRouteProgressResponse toResponse(UserRouteProgress userRouteProgress);

    @Mapping(target = "routeId", source = "route.routeId")
    @Mapping(target = "hotspotProgressList", ignore = true)
    UserRouteProgressDetailResponse toDetailResponse(UserRouteProgress userRouteProgress);
}
