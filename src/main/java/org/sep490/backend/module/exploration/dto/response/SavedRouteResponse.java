package org.sep490.backend.module.exploration.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SavedRouteResponse {
    Long savedRouteId;
    Long routeId;
    LocalDateTime savedAt;
}
