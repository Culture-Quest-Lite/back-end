package org.sep490.backend.module.gamification.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.gamification.dto.request.XpHistoryRequest;
import org.sep490.backend.module.gamification.entity.XpHistory;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface XpHistoryMapper {

    XpHistory toEntity(XpHistoryRequest request, User user, Long balanceAfter);
}
