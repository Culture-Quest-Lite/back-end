package org.sep490.backend.module.authentication.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.authentication.dto.request.RegistrationRequest;
import org.sep490.backend.module.authentication.dto.response.RegistrationResponse;
import org.sep490.backend.module.authentication.entity.User;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    User toEntity(RegistrationRequest request);
    RegistrationResponse toResponse(User user);

}
