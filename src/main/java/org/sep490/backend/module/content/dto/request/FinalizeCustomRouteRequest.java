package org.sep490.backend.module.content.dto.request;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FinalizeCustomRouteRequest {
    Long routeId;
    String description;
}
