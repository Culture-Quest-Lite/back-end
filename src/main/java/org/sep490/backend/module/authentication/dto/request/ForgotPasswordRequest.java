package org.sep490.backend.module.authentication.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequest {
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Định dạng Email không hợp lệ")
    private String email;

    /**
     * Nguồn gửi yêu cầu: {@code MOBILE} thì email chứa link mở app, còn lại (mặc định) là link web.
     */
    private String platform;
}
