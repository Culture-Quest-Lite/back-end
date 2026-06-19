package org.sep490.backend.module.user.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LevelRequest {

    @NotBlank(message = "Tên cấp bậc không được để trống")
    @Size(max = 100, message = "Tên cấp bậc không được vượt quá 100 kí tự")
    private String name;

    @NotNull(message = "XP yêu cầu không được để trống")
    @Min(value = 0, message = "XP yêu cầu phải lớn hơn hoặc bằng 0")
    private Integer requiredXp;

    @Size(max = 255, message = "Mô tả không được vượt quá 255 kí tự")
    private String description;
}
