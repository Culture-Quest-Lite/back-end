package org.sep490.backend.module.planner.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.content.mapper.HotspotMapper;
import org.sep490.backend.module.planner.dto.request.CreateCustomPlanRequest;
import org.sep490.backend.module.planner.dto.request.PlanStopRequest;
import org.sep490.backend.module.planner.dto.response.PlanHotspotResponse;
import org.sep490.backend.module.planner.dto.response.UserPlanResponse;
import org.sep490.backend.module.planner.entity.PlanHotspot;
import org.sep490.backend.module.planner.entity.UserPlan;

@Mapper(
        componentModel = "spring",
        uses = {HotspotMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface PlanMapper {

    @Mapping(source = "planHotspots", target = "stops")
    UserPlanResponse toResponse(UserPlan plan);

    PlanHotspotResponse toStopResponse(PlanHotspot planHotspot);

    @Mapping(target = "isOptimized", expression = "java(Boolean.TRUE.equals(request.getIsOptimized()))")
    UserPlan toEntity(CreateCustomPlanRequest request);

    @Mapping(target = "isOptimized", expression = "java(Boolean.TRUE.equals(request.getIsOptimized()))")
    void updateFromRequest(@MappingTarget UserPlan plan, CreateCustomPlanRequest request);

    PlanHotspot toEntity(PlanStopRequest request);
}
