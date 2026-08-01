package org.sep490.backend.module.content.dto.request;

import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.entity.enumeration.ReviewTargetType;
import org.springframework.web.multipart.MultipartFile;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReviewRequest {
    MultipartFile[] files;

    @NotNull(message = "Loại đối tượng đánh giá không được để trống")
    ReviewTargetType targetType;

    @NotNull(message = "ID đối tượng đánh giá không được để trống")
    Long targetId;

    @NotNull(message = "Số sao đánh giá không được để trống")
    @Min(value = 1, message = "Số sao đánh giá phải từ 1 đến 5")
    @Max(value = 5, message = "Số sao đánh giá phải từ 1 đến 5")
    Integer rating;

    @Size(max = 2000, message = "Nội dung đánh giá không được vượt quá 2000 ký tự")
    String comment;
}
