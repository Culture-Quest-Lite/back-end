package org.sep490.backend.config.momo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.module.admin.dto.request.CreatePaymentRequest;
import org.sep490.backend.module.admin.dto.request.RefundRequest;
import org.sep490.backend.module.admin.dto.response.MomoPaymentResponse;
import org.sep490.backend.module.admin.dto.response.MomoRefundResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class MomoClient {

    private final MomoProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    public MomoPaymentResponse createPayment(long amount, String orderId,
                                             String requestId, String orderInfo) throws Exception {
        String rawSig = "accessKey=" + properties.getAccessKey()
                + "&amount=" + amount
                + "&extraData="
                + "&ipnUrl=" + properties.getIpnUrl()
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + properties.getPartnerCode()
                + "&redirectUrl=" + properties.getRedirectUrl()
                + "&requestId=" + requestId
                + "&requestType=payWithMethod";

        String signature = MomoSignatureUtil.hmacSHA256(rawSig, properties.getSecretKey());

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .partnerCode(properties.getPartnerCode())
                .requestId(requestId)
                .amount(amount)
                .orderId(orderId)
                .orderInfo(orderInfo)
                .redirectUrl(properties.getRedirectUrl())
                .ipnUrl(properties.getIpnUrl())
                .requestType("payWithMethod")
                .extraData("")
                .autoCapture(true)
                .lang("vi")
                .signature(signature)
                .build();

        return restTemplate.postForObject(
                properties.getEndpoint() + "/v2/gateway/api/create", request, MomoPaymentResponse.class
        );
    }

        public MomoRefundResponse refund(long amount, String refundOrderId, String requestId, long transId, String description) throws Exception {
            String rawSig = "accessKey=" + properties.getAccessKey()
                    + "&amount=" + amount
                    + "&description=" + description
                    + "&orderId=" + refundOrderId
                    + "&partnerCode=" + properties.getPartnerCode()
                    + "&requestId=" + requestId
                    + "&transId=" + transId;

            String signature = MomoSignatureUtil.hmacSHA256(rawSig, properties.getSecretKey());
            RefundRequest body = RefundRequest.builder()
                    .partnerCode(properties.getPartnerCode())
                    .orderId(refundOrderId)
                    .requestId(requestId)
                    .amount(amount)
                    .transId(transId)
                    .description(description)
                    .lang("vi")
                    .signature(signature)
                    .build();
            log.info("[MoMo] refund refundOrderId={}, amount={}, transId={}", refundOrderId, amount, transId);
            return restTemplate.postForObject(
                    properties.getEndpoint() + "/v2/gateway/api/refund", body, MomoRefundResponse.class);
        }
}
