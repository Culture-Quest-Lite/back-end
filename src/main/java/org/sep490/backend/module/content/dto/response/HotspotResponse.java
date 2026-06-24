package org.sep490.backend.module.content.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.enums.ContentStatus;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;


@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HotspotResponse {
    Long hotspotId;
    List<TagResponse> tags;
    Long createByUserId;
    String hotspotName;
    String address;
    String description;
    String historyInformation;
    Double latitude;
    Double longitude;
    Double checkInRadius;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    Long xp;
    Long point;
    Long estimatedDurationMin;
    Long estimatedDurationMax;

    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(type = "string")
    LocalTime startTime;

    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(type = "string")
    LocalTime endTime;

    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(type = "string")
    LocalTime openingTime;

    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(type = "string")
    LocalTime closingTime;

    ContentStatus status;
    List<MediaResponse> medias;
}
