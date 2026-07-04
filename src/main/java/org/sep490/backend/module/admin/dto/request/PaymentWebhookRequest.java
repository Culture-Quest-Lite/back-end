package org.sep490.backend.module.admin.dto.request;

import lombok.Data;

@Data
public class PaymentWebhookRequest {
    private String code;
    private String desc;
    private Boolean success;
    private PayOsWebhookData data;
    private String signature;
    @Data
    public static class PayOsWebhookData {
        private Long orderCode;
        private Long amount;
        private String description;
        private String accountNumber;
        private String reference;
        private String transactionDateTime;
        private String currency;
        private String paymentLinkId;
        private String code;
        private String desc;
    }
}
