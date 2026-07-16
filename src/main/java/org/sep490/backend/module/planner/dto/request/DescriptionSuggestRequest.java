package org.sep490.backend.module.planner.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DescriptionSuggestRequest {

    @NotBlank
    String description;

    Double latitude;
    Double longitude;
    List<Long> anchorHotspotIds; // địa điểm user đã chọn
    Double radiusInMeters;
    Integer limit;
}
