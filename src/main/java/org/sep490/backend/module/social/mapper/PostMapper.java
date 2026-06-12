package org.sep490.backend.module.social.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.social.dto.request.PostRequest;
import org.sep490.backend.module.social.dto.response.PostResponse;
import org.sep490.backend.module.social.entity.Post;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PostMapper {

    @Mapping(target = "taggedHotspots", ignore = true)
    @Mapping(target = "taggedRoutes", ignore = true)
    @Mapping(target = "tags", ignore = true)
    Post toEntity(PostRequest request);

    @Mapping(source = "user.userId", target = "userId")
    @Mapping(source = "user.username", target = "username")
    @Mapping(source = "user.displayName", target = "displayName")
    @Mapping(target = "hotspotIds", expression = "java(mapHotspotsToIds(post.getTaggedHotspots()))")
    @Mapping(target = "routeIds", expression = "java(mapRoutesToIds(post.getTaggedRoutes()))")
    @Mapping(target = "tags", expression = "java(mapTagsToDtos(post.getTags()))")
    PostResponse toResponse(Post post);

    default List<Long> mapHotspotsToIds(Set<Hotspot> hotspots) {
        if (hotspots == null) return List.of();
        return hotspots.stream().map(Hotspot::getHotspotId).toList();
    }

    default List<Long> mapRoutesToIds(Set<Route> routes) {
        if (routes == null) return List.of();
        return routes.stream().map(Route::getRouteId).toList();
    }

    default List<PostResponse.TagDto> mapTagsToDtos(Set<Tag> tags) {
        if (tags == null) return List.of();
        return tags.stream().map(tag -> {
            PostResponse.TagDto dto = new PostResponse.TagDto();
            dto.setTagId(tag.getTagId());
            dto.setTagName(tag.getTagName());
            return dto;
        }).toList();
    }
}
