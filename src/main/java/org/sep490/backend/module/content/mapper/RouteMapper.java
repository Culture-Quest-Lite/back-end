package org.sep490.backend.module.content.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.content.dto.request.RouteRequest;
import org.sep490.backend.module.content.dto.response.RouteHotspotResponse;
import org.sep490.backend.module.content.dto.response.RouteResponse;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.RouteHotspot;
import org.sep490.backend.module.content.entity.Tag;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface RouteMapper {

    @Mapping(source = "tagIds", target = "tags")
    Route toEntity(RouteRequest request);

    @Mapping(source = "tagIds", target = "tags")
    void updateFromRequest(@MappingTarget Route route, RouteRequest request);

    @Mapping(target = "hotspots", ignore = true)
    RouteResponse toResponse(Route route);

    @Mapping(source = "route.routeId", target = "routeId")
    @Mapping(source = "hotspot.hotspotId", target = "hotspotId")
    @Mapping(source = "routeHotspot.hotspot.hotspotName", target = "hotspotName")
    @Mapping(source = "routeHotspot.hotspot.address", target = "address")
    @Mapping(source = "routeHotspot.hotspot.xp", target = "xp")
    @Mapping(target = "latitude", expression = "java(routeHotspot.getHotspot() != null && routeHotspot.getHotspot().getLocation() != null ? routeHotspot.getHotspot().getLocation().getY() : null)")
    @Mapping(target = "longitude", expression = "java(routeHotspot.getHotspot() != null && routeHotspot.getHotspot().getLocation() != null ? routeHotspot.getHotspot().getLocation().getX() : null)")
    RouteHotspotResponse toRouteHotspotResponse(RouteHotspot routeHotspot);

    List<RouteHotspotResponse> toRouteHotspotResponseList(List<RouteHotspot> routeHotspots);

    default Tag mapIdToTag(Long id) {
        if (id == null) {
            return null;
        }
        Tag tag = new Tag();
        tag.setTagId(id);
        return tag;
    }
}
