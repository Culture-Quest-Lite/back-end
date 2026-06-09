package org.sep490.backend.module.authentication.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.authentication.dto.request.RegistrationRequest;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.user.dto.response.UserProfileResponse;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    User toEntity(RegistrationRequest request);

    @Mapping(target = "levelName", source = "user.level.name")
    UserProfileResponse toProfileResponse(User user);
}
