package org.sep490.backend.module.admin.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubscriptionPlanRequest {

    @NotBlank(message = "Tên gói dịch vụ không được để trống")
    @Size(max = 100, message = "Tên gói dịch vụ không được vượt quá 100 ký tự")
    String subscriptionPlanName;

    @Size(max = 500, message = "Mô tả không được vượt quá 500 ký tự")
    String subscriptionPlanDescription;

    @NotNull(message = "Vui lòng nhập giá tháng")
    @Min(value = 0, message = "Giá tháng không được âm")
    Long priceMonthly;

    @NotNull(message = "Vui lòng nhập giá năm")
    @Min(value = 0, message = "Giá năm không được âm")
    Long priceYearly;

    Map<String, Object> configLimit;
}
