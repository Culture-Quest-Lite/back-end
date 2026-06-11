package org.sep490.backend.module.content.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.content.dto.request.RouteRequest;
import org.sep490.backend.module.content.dto.response.RouteHotspotResponse;
import org.sep490.backend.module.content.dto.response.RouteResponse;
import org.sep490.backend.module.content.entity.Category;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.RouteHotspot;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface RouteMapper {

    @Mapping(source = "categoryIds", target = "categories")
    Route toEntity(RouteRequest request);

    @Mapping(source = "categoryIds", target = "categories")
    void updateFromRequest(@MappingTarget Route route, RouteRequest request);

    @Mapping(target = "hotspots", ignore = true)
    RouteResponse toResponse(Route route);

    @Mapping(source = "route.routeId", target = "routeId")
    @Mapping(source = "hotspot.hotspotId", target = "hotspotId")
    RouteHotspotResponse toRouteHotspotResponse(RouteHotspot routeHotspot);

    List<RouteHotspotResponse> toRouteHotspotResponseList(List<RouteHotspot> routeHotspots);

    default Category mapIdToCategory(Long id) {
        if (id == null) {
            return null;
        }
        Category category = new Category();
        category.setCategoryId(id);
        return category;
    }
}
