package org.sep490.backend.module.content.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.sep490.backend.module.content.entity.enumeration.ContentType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoryResponse {
    Long storyId;
    TagResponse tag;
    Integer orderIndex;
    String title;
    String content;
    ContentStatus status;
    Double distanceToNext;
    String audioScript;
    List<MediaResponse> medias;
    Long hotspotId;
    Long routeId;
    Double cultureScore;
    String cultureReason;
    String rejectReason;

    Double averageRating;
    Long totalReviews;

    ContentType contentType;
    LocalDate validFrom;
    LocalDate validTo;
}
