package org.sep490.backend.module.exploration.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.exploration.dto.response.SavedRouteResponse;
import org.sep490.backend.module.exploration.entity.SavedRoute;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface SavedRouteMapper {

    @Mapping(source = "route.routeId", target = "routeId")
    SavedRouteResponse toResponse(SavedRoute savedRoute);

    @Mapping(source = "routeId", target = "route.routeId")
    @Mapping(source = "userId", target = "user.userId")
    SavedRoute toEntity(Long routeId, Long userId);
}
