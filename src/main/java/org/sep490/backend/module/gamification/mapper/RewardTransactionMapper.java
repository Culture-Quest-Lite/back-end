package org.sep490.backend.module.gamification.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.gamification.dto.request.RewardTransactionRequest;
import org.sep490.backend.module.gamification.dto.response.RewardTransactionResponse;
import org.sep490.backend.module.gamification.entity.RewardTransaction;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface RewardTransactionMapper {
    RewardTransactionResponse toResponse(RewardTransaction rewardTransaction);
    RewardTransaction toEntity(RewardTransactionRequest request, User user);
}
