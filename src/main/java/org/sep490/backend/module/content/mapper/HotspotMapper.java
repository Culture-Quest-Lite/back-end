package org.sep490.backend.module.content.mapper;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.content.dto.request.HotspotRequest;
import org.sep490.backend.module.content.dto.response.CategoryResponse;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.content.entity.Category;
import org.sep490.backend.module.content.entity.Hotspot;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface HotspotMapper {
    GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mapping(source = "createdBy.userId", target = "createByUserId")
    @Mapping(target = "latitude", expression = "java(hotspot.getLocation() != null ? hotspot.getLocation().getY() : null)")
    @Mapping(target = "longitude", expression = "java(hotspot.getLocation() != null ? hotspot.getLocation().getX() : null)")
    HotspotResponse toResponse(Hotspot hotspot);

    CategoryResponse toCategoryResponse(Category category);

    @Mapping(source = "categoryIds", target = "categories")
    @Mapping(target = "location", expression = "java(toPoint(request.getLongitude(), request.getLatitude()))")
    void updateFromRequest(@MappingTarget Hotspot hotspot, HotspotRequest request);

    @Mapping(source = "categoryIds", target = "categories")
    @Mapping(target = "location", expression = "java(toPoint(request.getLongitude(), request.getLatitude()))")
    Hotspot toEntity(HotspotRequest request);

    default Category mapIdToCategory(Long id) {
        if (id == null) {
            return null;
        }
        Category category = new Category();
        category.setCategoryId(id);
        return category;
    }

    default Point toPoint(Double longitude, Double latitude) {
        if (longitude == null || latitude == null) {
            return null;
        }
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }


}
