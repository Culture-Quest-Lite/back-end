package org.sep490.backend.module.content.dto.request;

import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.entity.enumeration.RouteDifficulty;
import org.sep490.backend.module.content.entity.enumeration.RouteStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RouteCreateRequest {

    MultipartFile[] files;

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

    @NotNull(message = "Tag ID của tuyến đường không được để trống")
    Long tagId;

    @NotEmpty(message = "Tuyến đường phải có ít nhất 4 điểm dừng (hotspot)")
    @Size(min = 4, message = "Tuyến đường phải có ít nhất 4 điểm dừng")
    List<Long> storyIds;

    @NotNull(message = "Kinh nghiệm không được để trống")
    @PositiveOrZero(message = "Kinh nghiệm không được là số âm")
    Long xp;

    @NotNull(message = "Điểm thưởng không được để trống")
    @PositiveOrZero(message = "Điểm thưởng không được là số âm")
    Long point;

    RouteStatus status;
}
