package org.sep490.backend.module.gamification.service;

import org.sep490.backend.module.gamification.dto.request.RewardTransactionRequest;
import org.sep490.backend.module.gamification.dto.response.RewardTransactionResponse;
import org.sep490.backend.module.partner.dto.filter.VoucherFilter;
import org.springframework.data.domain.Page;

public interface RewardTransactionService {
    Page<RewardTransactionResponse> getMyRewardHistory(VoucherFilter filter);
    void createRewardTransaction(RewardTransactionRequest request);
}
