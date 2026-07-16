package org.sep490.backend.module.content.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.content.dto.request.StoryRequest;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.dto.response.TagResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.entity.Tag;

@Mapper(
        componentModel = "spring",
        uses = {MediaMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface StoryMapper {

    @Mapping(source = "hotspot.hotspotId", target = "hotspotId")
    @Mapping(source = "route.routeId", target = "routeId")
    StoryResponse toResponse(Story story);

    TagResponse toTagResponse(Tag tag);

    @Mapping(source = "tagId", target = "tag")
    @Mapping(target = "hotspot", ignore = true)
    @Mapping(target = "route", ignore = true)
    void updateFromRequest(@MappingTarget Story story, StoryRequest storyRequest);

    @Mapping(source = "tagId", target = "tag")
    @Mapping(target = "hotspot", ignore = true)
    @Mapping(target = "route", ignore = true)
    @Mapping(target = "orderIndex", ignore = true)
    Story toEntity(StoryRequest storyRequest);

    default Tag mapIdToTag(Long id) {
        if (id == null) {
            return null;
        }
        Tag tag = new Tag();
        tag.setTagId(id);
        return tag;
    }

    default Hotspot mapIdToHotspot(Long id) {
        if (id == null) {
            return null;
        }
        Hotspot hotspot = new Hotspot();
        hotspot.setHotspotId(id);
        return hotspot;
    }
}
