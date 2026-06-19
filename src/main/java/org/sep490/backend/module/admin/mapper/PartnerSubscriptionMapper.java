package org.sep490.backend.module.admin.mapper;

import org.locationtech.jts.geom.Point;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.admin.dto.request.PartnerSubscriptionRequest;
import org.sep490.backend.module.admin.dto.response.PartnerSubscriptionResponse;
import org.sep490.backend.module.admin.entity.PartnerSubscription;
import org.sep490.backend.module.content.entity.Media;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PartnerSubscriptionMapper {

    @Mapping(target = "location", expression = "java(org.sep490.backend.common.utils.SpatialUtils.fromCoordinates(request.getLongitude(), request.getLatitude()))")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "partner", ignore = true)
    @Mapping(target = "subscriptionPlan", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "startDate", ignore = true)
    @Mapping(target = "endDate", ignore = true)
    PartnerSubscription toEntity(PartnerSubscriptionRequest request);

    @Mapping(source = "partner.userId", target = "partnerId")
    @Mapping(source = "partner.username", target = "partnerName")
    @Mapping(source = "subscriptionPlan.subscriptionPlanId", target = "subscriptionPlanId")
    @Mapping(source = "subscriptionPlan.subscriptionPlanName", target = "subscriptionPlanName")
    @Mapping(source = "location", target = "longitude", qualifiedByName = "getLongitude")
    @Mapping(source = "location", target = "latitude", qualifiedByName = "getLatitude")
    PartnerSubscriptionResponse toResponse(PartnerSubscription entity);

    @Named("getLongitude")
    default Double getLongitude(Point coordinate) {
        return coordinate != null ? coordinate.getX() : null;
    }

    @Named("getLatitude")
    default Double getLatitude(Point coordinate) {
        return coordinate != null ? coordinate.getY() : null;
    }

    @Mapping(target = "mediaType", expression = "java(media.getMediaType() != null ? media.getMediaType().name() : null)")
    PartnerSubscriptionResponse.MediaDto toMediaDto(Media media);
}
