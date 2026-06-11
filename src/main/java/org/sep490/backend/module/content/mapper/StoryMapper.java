package org.sep490.backend.module.content.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.content.dto.request.StoryRequest;
import org.sep490.backend.module.content.dto.response.CategoryResponse;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.entity.Category;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Story;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface StoryMapper {

    @Mapping(source = "hotspot.hotspotId", target = "hotspotId")
    StoryResponse toResponse(Story story);
    CategoryResponse toCategoryResponse(Category category);

    @Mapping(source = "categoryId", target = "category")
    @Mapping(source = "hotspotId", target = "hotspot")
    void updateFromRequest(@MappingTarget Story story, StoryRequest storyRequest);

    @Mapping(source = "categoryId", target = "category")
    @Mapping(source = "hotspotId", target = "hotspot")
    Story toEntity(StoryRequest storyRequest);

    default Category mapIdToCategory(Long id) {
        if (id == null) {
            return null;
        }
        Category category = new Category();
        category.setCategoryId(id);
        return category;
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
