package org.sep490.backend.module.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.user.dto.response.LevelProgressResponse;
import org.sep490.backend.module.user.entity.LevelProgress;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LevelProgressMapper {

    @Mapping(target = "levelId", source = "levelProgress.level.levelId")
    @Mapping(target = "userId", source = "levelProgress.user.userId")
    @Mapping(target = "levelName", source = "levelProgress.level.name")
    @Mapping(target = "requiredXp", source = "levelProgress.level.requiredXp")
    LevelProgressResponse toResponse(LevelProgress levelProgress);
}
