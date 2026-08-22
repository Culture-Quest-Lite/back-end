package org.sep490.backend.module.content.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.entity.enumeration.ContentType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HotspotRequest {
    MultipartFile[] files;

//    @NotEmpty(message = "Hotspot phải gắn với ít nhất 1 story")
//    List<Long> storyIds;

    @NotBlank(message = "Tên địa điểm không được để trống")
    @Size(max = 100, message = "Tên địa điểm không được vượt quá 100 ký tự")
    String hotspotName;

    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    String address;

    @Size(max = 2000, message = "Mô tả không được vượt quá 2000 ký tự")
    String description;

    @Size(max = 2000, message = "Thông tin lịch sử không được vượt quá 2000 ký tự")
    String historyInformation;

    // check location thuộc VN ở service
    @NotNull(message = "Vĩ độ không được để trống")
    Double latitude;

    @NotNull(message = "Kinh độ không được để trống")
    Double longitude;

    // Khoảng cho phép do admin cấu hình (GET /api/v1/configs/check-in-radius), kiểm tra ở service
    @Positive(message = "Bán kính check-in phải lớn hơn 0")
    @Schema(description = "Bán kính vùng check-in (mét). Bỏ trống sẽ dùng bán kính mặc định do admin cấu hình",
            example = "800")
    Integer checkInRadius;

    @Schema(description = "Ranh giới GeoJSON dạng Polygon; để trống nếu dùng bán kính",
            example = "{\"type\":\"Polygon\",\"coordinates\":[[[105.851,21.028],[105.855,21.028],[105.855,21.031],[105.851,21.031],[105.851,21.028]]]}")
    String boundaryGeoJson;

    @NotNull(message = "Thời gian dự kiến tối thiểu không được để trống")
    @PositiveOrZero(message = "Thời gian dự kiến tối thiểu không được là số âm")
    Long estimatedDurationMin;

    @NotNull(message = "Thời gian dự kiến tối đa không được để trống")
    @PositiveOrZero(message = "Thời gian dự kiến tối đa không được là số âm")
    Long estimatedDurationMax;

    @NotNull(message = "Thời gian bắt đầu đẹp trong ngày không được để trống")
    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(type = "string", example = "08:00:00")
    LocalTime startTime;

    @NotNull(message = "Thời gian kết thúc đẹp trong ngày không được để trống")
    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(type = "string", example = "09:30:00")
    LocalTime endTime;

    // allow null
    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(type = "string", example = "08:00:00")
    LocalTime openingTime;
    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(type = "string", example = "22:00:00")
    LocalTime closingTime;

    //ContentStatus status;

    ContentType contentType;
    @JsonFormat(pattern = "dd/MM/yyyy")
    @DateTimeFormat(pattern = "dd/MM/yyyy")
    @Schema(type = "string", example = "22/08/2026")
    LocalDate validFrom;
    @JsonFormat(pattern = "dd/MM/yyyy")
    @DateTimeFormat(pattern = "dd/MM/yyyy")
    @Schema(type = "string", example = "23/08/2026")
    LocalDate validTo;
}
