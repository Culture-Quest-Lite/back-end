package org.sep490.backend.module.content.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.content.dto.request.RouteCreateRequest;
import org.sep490.backend.module.content.dto.request.RouteRequest;
import org.sep490.backend.module.content.dto.response.RouteResponse;
import org.sep490.backend.module.content.entity.Route;

@Mapper(
        componentModel = "spring",
        uses = {StoryMapper.class, MediaMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface RouteMapper {

    @Mapping(target = "stories", ignore = true)
    @Mapping(target = "tag", ignore = true)
    Route toEntity(RouteRequest request);

    @Mapping(target = "stories", ignore = true)
    @Mapping(target = "tag", ignore = true)
    Route toEntity(RouteCreateRequest request);

    @Mapping(target = "stories", ignore = true)
    @Mapping(target = "tag", ignore = true)
    void updateFromRequest(@MappingTarget Route route, RouteRequest request);

    RouteResponse toResponse(Route route);
}
