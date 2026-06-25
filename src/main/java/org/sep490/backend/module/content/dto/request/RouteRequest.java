package org.sep490.backend.module.content.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.entity.enumeration.RouteDifficulty;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RouteRequest {
    @NotBlank(message = "Tên tuyến đường không được để trống")
    @Size(max = 100, message = "Tên tuyến đường không được vượt quá 100 ký tự")
    String routeName;

    @Size(max = 2000, message = "Mô tả không được vượt quá 2000 ký tự")
    String description;

    @NotNull(message = "Độ khó của tuyến đường không được để trống")
    RouteDifficulty difficulty;

    @NotNull(message = "Thời gian ước tính không được để trống")
    @Positive(message = "Thời gian ước tính phải lớn hơn 0")
    Double estimateTime;

    @NotNull(message = "Tổng khoảng cách không được để trống")
    @Positive(message = "Tổng khoảng cách phải lớn hơn 0")
    Double totalDistance;

    @NotEmpty(message = "Tuyến đường phải có ít nhất 4 điểm dừng (Hotspot)")
    @Valid
    List<RouteHotspotRequest> hotspots;

    @NotEmpty(message = "Tuyến đường phải thuộc ít nhất 1 tag")
    List<Long> tagIds;

    @NotNull(message = "Kinh nghiệm không được để trống")
    @PositiveOrZero(message = "Kinh nghiệm không được là số âm")
    Long xp;

    @NotNull(message = "Điểm thưởng không được để trống")
    @PositiveOrZero(message = "Điểm thưởng không được là số âm")
    Long point;
}
