package org.sep490.backend.module.planner.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateCustomPlanRequest {

    @NotBlank(message = "Tên kế hoạch không được để trống")
    String name;
    String description;

    @NotEmpty(message = "Kế hoạch phải có ít nhất 1 điểm dừng")
    @Valid
    List<PlanStopRequest> stops;

    Double startLatitude;
    Double startLongitude;
    Boolean isOptimized;
}
