package org.sep490.backend.module.admin.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PartnerSubscriptionResponse {
    Long id;
    Long partnerId;
    String partnerName;
    Long subscriptionPlanId;
    String subscriptionPlanName;
    String shopName;
    String address;
    Double longitude;
    Double latitude;
    String billingCycle;
    String status;
    LocalDateTime startDate;
    LocalDateTime endDate;
    Boolean isVerified;
    String documentUrl;
    List<MediaDto> medias;

    @Data
    public static class MediaDto {
        Long mediaId;
        String fileUrl;
        String fileName;
        String mediaType;
    }
}
