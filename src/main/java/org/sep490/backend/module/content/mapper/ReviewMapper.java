package org.sep490.backend.module.content.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.content.dto.request.ReviewUpdateRequest;
import org.sep490.backend.module.content.dto.response.ReviewResponse;
import org.sep490.backend.module.content.entity.Review;

@Mapper(
        componentModel = "spring",
        uses = {MediaMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ReviewMapper {

    @Mapping(source = "user.userId", target = "userId")
    @Mapping(source = "user.username", target = "username")
    @Mapping(source = "user.displayName", target = "displayName")
    @Mapping(source = "user.avatarUrl", target = "avatarUrl")
    @Mapping(target = "isOwner", ignore = true)
    ReviewResponse toResponse(Review review);

    @Mapping(target = "medias", ignore = true)
    void updateFromRequest(@MappingTarget Review review, ReviewUpdateRequest request);
}
