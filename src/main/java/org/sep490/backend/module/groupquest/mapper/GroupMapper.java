package org.sep490.backend.module.groupquest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.groupquest.dto.response.GroupResponse;
import org.sep490.backend.module.groupquest.entity.Group;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface GroupMapper {
    @Mapping(target = "createdBy", source = "createdBy.userId")
    @Mapping(target = "inviteLink", expression = "java(group.getShareToken() != null ? \"https://api.culturequestlite.com/api/v1/groups/join/\" + group.getShareToken() : null)")
    GroupResponse toResponse(Group group);
}
