package org.sep490.backend.module.admin.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sep490.backend.module.admin.entity.enumeration.BillingCycleEnum;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PartnerSubscriptionRequest {

    @NotNull(message = "Vui lòng chọn gói dịch vụ")
    private Long subscriptionPlanId;

    @NotBlank(message = "Tên quán không được trống")
    private String shopName;

    @NotBlank(message = "Email quản lý shop không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String shopEmail;

    @NotBlank(message = "Địa chỉ quán không được trống")
    private String address;

    @NotNull(message = "Kinh độ không được trống")
    private Double longitude;

    @NotNull(message = "Vĩ độ không được trống")
    private Double latitude;

    private MultipartFile documentFile;

    private BillingCycleEnum billingCycle;
}
