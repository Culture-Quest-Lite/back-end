package org.sep490.backend.module.content.mapper;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.common.utils.SpatialUtils;
import org.sep490.backend.module.content.dto.request.HotspotRequest;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.content.entity.Hotspot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Mapper(
        componentModel = "spring",
        uses = {MediaMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface HotspotMapper {
    GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mapping(source = "createdBy.userId", target = "createByUserId")
    @Mapping(target = "latitude", expression = "java(hotspot.getLocation() != null ? hotspot.getLocation().getY() : null)")
    @Mapping(target = "longitude", expression = "java(hotspot.getLocation() != null ? hotspot.getLocation().getX() : null)")
    @Mapping(target = "stories", ignore = true)
    HotspotResponse toResponse(Hotspot hotspot);

    @Mapping(target = "location", expression = "java(toPoint(request.getLongitude(), request.getLatitude()))")
    void updateFromRequest(@MappingTarget Hotspot hotspot, HotspotRequest request);

    @Mapping(target = "location", expression = "java(toPoint(request.getLongitude(), request.getLatitude()))")
    @Mapping(source = "point", target = "point")
    Hotspot toEntity(HotspotRequest request);

    default Point toPoint(Double longitude, Double latitude) {
        return SpatialUtils.fromCoordinates(longitude, latitude);
    }

    default LocalTime map(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.toLocalTime();
    }

    default LocalDateTime map(LocalTime value) {
        if (value == null) {
            return null;
        }
        return value.atDate(LocalDate.now());
    }
}
