package org.sep490.backend.module.gamification.service;

import org.sep490.backend.module.gamification.dto.response.PointTransactionResponse;
import org.sep490.backend.module.partner.dto.filter.VoucherFilter;
import org.springframework.data.domain.Page;


public interface PointTransactionService {
    Page<PointTransactionResponse> getMyPointHistory(VoucherFilter filter);
}
