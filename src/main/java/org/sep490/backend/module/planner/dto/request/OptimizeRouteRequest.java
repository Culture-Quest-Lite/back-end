package org.sep490.backend.module.planner.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.planner.entity.enumeration.OptimizeCriterion;

import java.time.LocalTime;
import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OptimizeRouteRequest {

    @NotEmpty(message = "Cần ít nhất 2 điểm dừng để tối ưu")
    List<Long> hotspotIds;

    Double startLatitude;
    Double startLongitude;

    OptimizeCriterion criterion = OptimizeCriterion.TIME;

    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(type = "string", example = "08:00:00")
    LocalTime startTime;
}
