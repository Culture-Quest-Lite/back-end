package org.sep490.backend.module.admin.mapper;

import org.locationtech.jts.geom.Point;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.admin.dto.request.PartnerSubscriptionRequest;
import org.sep490.backend.module.admin.dto.response.PartnerSubscriptionResponse;
import org.sep490.backend.module.admin.entity.Invoice;
import org.sep490.backend.module.admin.entity.PartnerInfo;
import org.sep490.backend.module.content.entity.Media;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PartnerSubscriptionMapper {

    @Mapping(target = "location", expression = "java(org.sep490.backend.common.utils.SpatialUtils.fromCoordinates(request.getLongitude(), request.getLatitude()))")
    @Mapping(target = "partnerInfoId", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PartnerInfo toPartnerInfo(PartnerSubscriptionRequest request);

    @Mapping(source = "invoice.invoiceId", target = "id")
    @Mapping(source = "invoice.partnerInfo.user.userId", target = "partnerId")
    @Mapping(source = "invoice.partnerInfo.user.displayName", target = "partnerName")
    @Mapping(source = "invoice.subscriptionPlan.subscriptionPlanId", target = "subscriptionPlanId")
    @Mapping(source = "invoice.subscriptionPlan.subscriptionPlanName", target = "subscriptionPlanName")
    @Mapping(source = "invoice.partnerInfo.shopName", target = "shopName")
    @Mapping(source = "invoice.partnerInfo.address", target = "address")
    @Mapping(source = "invoice.partnerInfo.location", target = "longitude", qualifiedByName = "getLongitude")
    @Mapping(source = "invoice.partnerInfo.location", target = "latitude", qualifiedByName = "getLatitude")
    @Mapping(source = "invoice.partnerInfo.documentUrl", target = "documentUrl")
    @Mapping(target = "isVerified", expression =
            "java(invoice.getPartnerInfo() != null && invoice.getPartnerInfo().getStatus() != null && \"ACTIVE\".equals(invoice.getPartnerInfo().getStatus().name()))")
    @Mapping(source = "invoice.billingCycle", target = "billingCycle")
    @Mapping(source = "invoice.status", target = "status")
    @Mapping(source = "invoice.startDate", target = "startDate")
    @Mapping(source = "invoice.endDate", target = "endDate")
    @Mapping(target = "medias", ignore = true)
    PartnerSubscriptionResponse toResponse(Invoice invoice);

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
