package org.sep490.backend.module.content.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.sep490.backend.module.content.dto.response.MediaResponse;
import org.sep490.backend.module.content.entity.Media;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MediaMapper {
    @Mapping(target = "mediaType", expression = "java(media.getMediaType() != null ? media.getMediaType().name() : null)")
    MediaResponse toResponse(Media media);
}
