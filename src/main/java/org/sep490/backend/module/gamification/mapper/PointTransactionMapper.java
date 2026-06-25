package org.sep490.backend.module.gamification.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.gamification.dto.request.PointTransactionRequest;
import org.sep490.backend.module.gamification.dto.response.PointTransactionResponse;
import org.sep490.backend.module.gamification.entity.PointTransaction;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface PointTransactionMapper {
    @Mapping(source = "transactionId", target = "id")
    PointTransactionResponse toResponse(PointTransaction pointHistory);

    PointTransaction toEntity(PointTransactionRequest pointTransactionRequest, User user);
}
