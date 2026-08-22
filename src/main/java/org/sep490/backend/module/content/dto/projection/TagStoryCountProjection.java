package org.sep490.backend.module.content.dto.projection;

public interface TagStoryCountProjection {
    Long getTagId();
    Long getStoryCount();
    Long getHotspotCount();
}
