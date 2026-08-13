package org.sep490.backend.module.user.dto.request;

import lombok.Data;

@Data
public class CancelSubscriptionRequest {
    private String reason;
}
