package org.sep490.backend.module.groupquest.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.Length;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GroupUpdateRequest {

    @NotBlank(message = "Tên group không được để trống")
    @Length(max = 255, message = "Tên group không được vượt quá 255 ký tự")
    String groupName;
    Boolean requiredApproval;
}
