package org.sep490.backend.module.exploration.mapper;

import org.locationtech.jts.geom.Point;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.common.utils.SpatialUtils;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.exploration.dto.request.CheckInRequest;
import org.sep490.backend.module.exploration.dto.response.CheckInResponse;
import org.sep490.backend.module.exploration.entity.CheckIn;
import org.sep490.backend.module.exploration.entity.UserRouteProgress;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface CheckInMapper {

    @Mapping(target = "pointEarned", source = "hotspot.point")
    @Mapping(target = "xpEarned", source = "hotspot.xp")
    @Mapping(target = "checkInLocation", expression = "java(toPoint(request.getLongitude(), request.getLatitude()))")
    @Mapping(target = "hotspot", source = "hotspot")
    CheckIn toEntity(CheckInRequest request, User user, Hotspot hotspot, UserRouteProgress userRouteProgress, double distanceToHotspot);

    @Mapping(target = "hotspotId", source = "hotspot.hotspotId")
    @Mapping(target = "userRouteProgressId", source = "userRouteProgress.userRouteProgressId")
    CheckInResponse toResponse(CheckIn checkIn);

    default Point toPoint(Double longitude, Double latitude) {
        return SpatialUtils.fromCoordinates(longitude, latitude);
    }
}
