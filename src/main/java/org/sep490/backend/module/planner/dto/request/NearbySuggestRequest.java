package org.sep490.backend.module.planner.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NearbySuggestRequest {

    @NotEmpty(message = "Cần ít nhất 1 địa điểm neo")
    List<Long> anchorHotspotIds;
    Double radiusInMeters;
    Integer limit;
}
