package org.sep490.backend.module.authentication.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegistrationRequest {

    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Size(min = 5, max = 50, message = "Tên đăng nhập phải có ít nhất 5 kí tự")
    private String username;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, message = "Mật khẩu phải có ít nhất 6 kí tự")
    private String password;

    @Size(max = 20, message = "Tên hiển thị không được vượt quá 20 ký tự")
    private String displayName;
}
