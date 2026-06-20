package org.sep490.backend.module.content.dto.request;

import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HotspotRequest {
    @NotEmpty(message = "Địa điểm phải thuộc ít nhất 1 tag")
    List<Long> tagIds;

    @NotBlank(message = "Tên địa điểm không được để trống")
    @Size(max = 100, message = "Tên địa điểm không được vượt quá 100 ký tự")
    String hotspotName;

    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    String address;

    @Size(max = 2000, message = "Mô tả không được vượt quá 2000 ký tự")
    String description;

    // check location thuộc VN ở service
    @NotNull(message = "Vĩ độ không được để trống")
    Double latitude;

    @NotNull(message = "Kinh độ không được để trống")
    Double longitude;

    @NotNull(message = "Bán kính check-in không được để trống")
    @Positive(message = "Bán kính check-in phải là số dương")
    @Max(value = 1000, message = "Bán kính check-in tối đa là 5000 mét")
    Double checkInRadius;

    @NotNull(message = "Điểm kinh nghiệm không được để trống")
    @PositiveOrZero(message = "Điểm kinh nghiệm không được là số âm")
    Long xp;

    @NotNull(message = "Điểm thưởng không được để trống")
    @PositiveOrZero(message = "Điểm thưởng không được là số âm")
    Long point;
}
