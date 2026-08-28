package org.sep490.backend.module.admin.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckInRadiusConfigResponse {

    Integer minRadius;

    Integer maxRadius;

    Integer defaultRadius;

    LocalDateTime updatedAt;

    String updatedBy;
}
