package org.sep490.backend.module.content.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RouteHotspotResponse {
    Long routeHotspotId;
    Long routeId;
    Long hotspotId;
    String hotspotName;
    String address;
    Long xp;
    Integer index;
    Double distanceToNext;
    Double latitude;
    Double longitude;
    List<MediaResponse> medias;
}
