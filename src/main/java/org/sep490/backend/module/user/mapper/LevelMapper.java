package org.sep490.backend.module.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.user.dto.request.LevelRequest;
import org.sep490.backend.module.user.dto.response.LevelResponse;
import org.sep490.backend.module.user.entity.Level;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LevelMapper {

    Level toEntity(LevelRequest request);

    LevelResponse toResponse(Level level);

    void updateEntityFromRequest(LevelRequest request, @MappingTarget Level level);
}
