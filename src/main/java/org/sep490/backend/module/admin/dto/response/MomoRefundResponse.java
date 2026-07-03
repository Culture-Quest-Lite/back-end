package org.sep490.backend.module.admin.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MomoRefundResponse {
    private String partnerCode;
    private String orderId;
    private String requestId;
    private long amount;
    private long transId;
    private int resultCode;
    private String message;
    private long responseTime;
}
