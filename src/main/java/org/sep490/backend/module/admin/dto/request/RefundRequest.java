package org.sep490.backend.module.admin.dto.request;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RefundRequest {
    private String partnerCode;
    private String orderId;
    private String requestId;
    private long amount;
    private long transId;
    private String description;
    private String lang;
    private String signature;
}
