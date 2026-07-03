package org.sep490.backend.module.admin.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MomoPaymentInitResponse {
    private Long subscriptionId;
    private String payUrl;       
    private String deeplink;    
    private String qrCodeUrl;
    private Long amount;
    private String orderInfo;
}

