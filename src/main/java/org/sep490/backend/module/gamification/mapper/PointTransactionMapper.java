package org.sep490.backend.module.gamification.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.sep490.backend.module.gamification.dto.response.PointTransactionResponse;
import org.sep490.backend.module.gamification.entity.PointTransaction;

@Mapper(componentModel = "spring")
public interface PointTransactionMapper {
    @Mapping(source = "transactionId", target = "id")
    PointTransactionResponse toResponse(PointTransaction pointHistory);
}
