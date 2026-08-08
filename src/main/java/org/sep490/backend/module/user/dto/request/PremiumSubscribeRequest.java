package org.sep490.backend.module.user.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.admin.entity.enumeration.BillingCycleEnum;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PremiumSubscribeRequest {

    @NotNull
    Long subscriptionPlanId;

    @NotNull
    BillingCycleEnum billingCycle;
    String redirectUrl;
}
