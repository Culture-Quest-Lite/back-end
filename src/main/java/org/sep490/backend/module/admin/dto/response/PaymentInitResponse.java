package org.sep490.backend.module.admin.dto.response;

import org.sep490.backend.module.admin.entity.enumeration.PaymentGateway;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentInitResponse {
    private Long subscriptionId;
    private PaymentGateway gateway;
    private String checkoutUrl;
    private String qrCode;
    private String payUrl;
    private String deeplink;
    private String qrCodeUrl;
    private Long amount;
    private String orderInfo;


}
