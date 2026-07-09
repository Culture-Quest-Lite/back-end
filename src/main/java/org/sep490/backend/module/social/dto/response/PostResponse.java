package org.sep490.backend.module.social.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.dto.response.MediaResponse;
import org.sep490.backend.module.social.entity.enumeration.PostStatus;
import org.sep490.backend.module.social.entity.enumeration.PostVisibility;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostResponse {

    Long postId;
    Long userId;
    String username;
    String displayName;
    String content;
    PostVisibility visibility;
    PostStatus status;
    String reason;
    Boolean isTaggedHotspot;
    Boolean isTaggedRoute;
    List<Long> hotspotIds;
    List<Long> routeIds;
    List<TagDto> tags;
    List<MediaResponse> medias;
    LocalDateTime createdAt;
    Integer pointRemaining;
    Long likeCount;
    Long commentCount;
    Long shareCount;
    PostResponse sharedPost;

    @Data
    public static class TagDto {
        Long tagId;
        String tagName;
    }
}
