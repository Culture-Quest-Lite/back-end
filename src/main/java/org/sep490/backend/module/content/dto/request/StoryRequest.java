package org.sep490.backend.module.content.dto.request;

import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoryRequest {
    @NotNull(message = "Category ID không được để trống")
    Long categoryId;

    @NotNull(message = "Hotspot ID không được để trống")
    Long hotspotId;

    @NotNull(message = "Thứ tự cốt truyện không được để trống")
    @PositiveOrZero(message = "Thứ tự cốt truyện phải từ 0 trở lên")
    Integer orderIndex;

    @NotBlank(message = "Tiêu đề cốt truyện không được để trống")
    @Size(max = 100, message = "Tiêu đề không được vượt quá 100 ký tự")
    String title;

    @NotBlank(message = "Nội dung cốt truyện không được để trống")
    String content;

    // Chặn URL sai định dạng để tránh lỗi khi Mobile App load Audio
    @Pattern(regexp = "^(http|https)://.*$", message = "URL âm thanh không hợp lệ (phải bắt đầu bằng http hoặc https)")
    String audioUrl;
}
