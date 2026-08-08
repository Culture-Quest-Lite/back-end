package org.sep490.backend.module.groupquest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.Length;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GroupRequest {

    MultipartFile[] files;

    @NotBlank(message = "Tên group không được để trống")
    @Length(max = 255, message = "Tên group không được vượt quá 255 ký tự")
    String groupName;

    @NotNull(message = "Bạn cần ít nhất 1 thành viên để tạo nhóm")
    List<Long> userIds;
}
