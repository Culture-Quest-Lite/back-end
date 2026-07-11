package org.sep490.backend.module.content.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.content.dto.filter.StoryFilterRequest;
import org.sep490.backend.module.content.dto.request.StoryRequest;
import org.sep490.backend.module.content.dto.response.MediaResponse;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.sep490.backend.module.content.entity.enumeration.MediaTargetType;
import org.sep490.backend.module.content.mapper.StoryMapper;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.content.repository.TagRepository;
import org.sep490.backend.module.content.service.inter.MediaService;
import org.sep490.backend.module.content.service.inter.StoryService;
import org.sep490.backend.module.content.specification.StorySpecification;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StoryServiceImpl implements StoryService {

    StoryRepository storyRepository;
    StoryMapper storyMapper;
    UserService userService;
    MediaService mediaService;
    TagRepository tagRepository;

    @Override
    @Transactional
    public StoryResponse create(StoryRequest storyRequest) {
        Tag tag = tagRepository.findById(storyRequest.getTagId())
                .orElseThrow(() -> new BusinessException("Tag không tồn tại với ID: " + storyRequest.getTagId()));

        Story story = storyMapper.toEntity(storyRequest);
        story.setCreatedBy(userService.getCurrentUser());
        story.setTag(tag);
        story.setStatus(ContentStatus.DRAFT);

        story = storyRepository.save(story);
        StoryResponse response = storyMapper.toResponse(story);

        if (storyRequest.getFiles() != null && storyRequest.getFiles().length > 0) {
            try {
                List<MediaResponse> mediaResponses = mediaService.uploadAndSaveMedias(
                        storyRequest.getFiles(), MediaTargetType.STORY, story.getStoryId());
                response.setMedias(mediaResponses);
            } catch (IOException e) {
                throw new BusinessException("Lỗi tải lên media: " + e.getMessage());
            }
        }
        return response;
    }

    @Override
    @Transactional
    public StoryResponse update(Long id, StoryRequest storyRequest) {
        Story story = getById(id);

        Tag tag = tagRepository.findById(storyRequest.getTagId())
                .orElseThrow(() -> new BusinessException("Tag không tồn tại với ID: " + storyRequest.getTagId()));

        storyMapper.updateFromRequest(story, storyRequest);
        story.setTag(tag);

        story = storyRepository.save(story);
        return storyMapper.toResponse(story);
    }

    @Override
    @Transactional(readOnly = true)
    public StoryResponse getDetail(Long id) {
        return storyMapper.toResponse(storyRepository.getOne(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoryResponse> getByHotspot(Long hotspotId, Long routeId) {
        List<Story> stories;
        if (routeId != null) {
            List<Long> routeTagIds = storyRepository.findTagIdsByRouteId(routeId);
            if (!routeTagIds.isEmpty()) {
                stories = storyRepository.findByHotspotOrderedByRouteTag(hotspotId, routeTagIds);
            } else {
                stories = storyRepository.findByHotspotOrderedByIndex(hotspotId);
            }
        } else {
            stories = storyRepository.findByHotspotOrderedByIndex(hotspotId);
        }
        return stories.stream()
                .map(storyMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Story story = getById(id);
        story.setStatus(ContentStatus.DELETED);
        storyRepository.save(story);
    }

    @Override
    @Transactional(readOnly = true)
    public Story getById(Long id) {
        return storyRepository.findById(id).orElseThrow(
                () -> new BusinessException("Không tìm thấy câu chuyện với id: " + id)
        );
    }

    @Override
    public Page<StoryResponse> getAll(StoryFilterRequest filter) {
        Sort sort = filter.getSortDir().equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(filter.getSortBy()).ascending()
                : Sort.by(filter.getSortBy()).descending();

        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Specification<Story> spec = StorySpecification.filter(filter);

        return storyRepository.findAll(spec, pageable).map(storyMapper::toResponse);
    }

    @Override
    @Transactional
    public StoryResponse updateStatus(Long id, ContentStatus status) {

        Story story = getById(id);
        story.setStatus(status);

        storyRepository.save(story);

        return storyMapper.toResponse(story);
    }
}
