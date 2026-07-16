package org.sep490.backend.module.groupquest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.groupquest.dto.response.GroupParticipantResponse;
import org.sep490.backend.module.groupquest.entity.GroupParticipant;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface GroupParticipantMapper {
    @Mapping(target = "userId", source = "user.userId")
    @Mapping(target = "groupId", source = "group.groupId")
    GroupParticipantResponse toResponse(GroupParticipant groupParticipant);
}
