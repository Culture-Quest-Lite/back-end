package org.sep490.backend.module.content.dto.request;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RouteHotspotRequest {
    Long hotspotId;
    Integer index;
}
