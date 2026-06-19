package org.sep490.backend.module.authentication.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    @NotBlank(message = "Mật khẩu cũ không được để trống")
    private String oldPassword;

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 6, message = "Mật khẩu mới tối thiểu phải từ 6 ký tự")
    private String newPassword;

    @NotBlank(message = "Xác nhận mật khẩu mới không được để trống")
    private String confirmPassword;

    public boolean isPasswordMatch() {
        return this.newPassword != null && this.newPassword.equals(this.confirmPassword);
    }
}

