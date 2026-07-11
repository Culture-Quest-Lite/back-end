package org.sep490.backend.module.exploration.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.exploration.dto.response.RouteParticipantDetailResponse;
import org.sep490.backend.module.exploration.dto.response.RouteParticipantResponse;
import org.sep490.backend.module.exploration.entity.RouteParticipant;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface RouteParticipantMapper {

    @Mapping(target = "totalStops", source = "route.totalStops")
    @Mapping(target = "status", constant = "IN_PROGRESS")
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "startedAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    RouteParticipant toEntity(Route route, User user, Integer completedStops, Double progressPercentage);

    @Mapping(target = "routeId", source = "route.routeId")
    @Mapping(target = "routeName", source = "route.routeName")
    RouteParticipantResponse toResponse(RouteParticipant routeParticipant);

    @Mapping(target = "routeId", source = "route.routeId")
    @Mapping(target = "routeName", source = "route.routeName")
    @Mapping(target = "hotspotProgressList", ignore = true)
    RouteParticipantDetailResponse toDetailResponse(RouteParticipant routeParticipant);
}
