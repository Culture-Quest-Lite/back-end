package org.sep490.backend.module.admin.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreatePaymentRequest {
    private String partnerCode;
    private String requestId;
    private long amount;
    private String orderId;
    private String orderInfo;
    private String redirectUrl;
    private String ipnUrl;
    private String requestType;
    private String extraData;
    @JsonProperty("autoCapture")
    private boolean autoCapture;
    private String lang;
    private String signature;
}
