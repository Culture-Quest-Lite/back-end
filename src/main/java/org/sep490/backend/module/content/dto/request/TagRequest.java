package org.sep490.backend.module.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TagRequest {

    @NotBlank(message = "Tên tag không được để trống")
    @Size(max = 50, message = "Tên tag không được vượt quá 50 ký tự")
    String tagName;
}
