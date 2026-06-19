package org.sep490.backend.module.content.mapper;

import org.locationtech.jts.geom.Coordinate;
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
import org.sep490.backend.module.content.dto.response.TagResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Tag;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface HotspotMapper {
    GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    //@Mapping(source = "createdBy.userId", target = "createByUserId")
    @Mapping(target = "latitude", expression = "java(hotspot.getLocation() != null ? hotspot.getLocation().getY() : null)")
    @Mapping(target = "longitude", expression = "java(hotspot.getLocation() != null ? hotspot.getLocation().getX() : null)")
    HotspotResponse toResponse(Hotspot hotspot);

    TagResponse toTagResponse(Tag tag);

    @Mapping(source = "tagIds", target = "tags")
    @Mapping(target = "location", expression = "java(toPoint(request.getLongitude(), request.getLatitude()))")
    void updateFromRequest(@MappingTarget Hotspot hotspot, HotspotRequest request);

    @Mapping(source = "tagIds", target = "tags")
    @Mapping(target = "location", expression = "java(toPoint(request.getLongitude(), request.getLatitude()))")
    Hotspot toEntity(HotspotRequest request);

    default Tag mapIdToTag(Long id) {
        if (id == null) {
            return null;
        }
        Tag tag = new Tag();
        tag.setTagId(id);
        return tag;
    }

    default Point toPoint(Double longitude, Double latitude) {
        return SpatialUtils.fromCoordinates(longitude, latitude);
    }


}
