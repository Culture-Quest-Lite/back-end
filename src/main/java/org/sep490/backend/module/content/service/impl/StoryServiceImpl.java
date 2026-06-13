package org.sep490.backend.module.content.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.dto.filter.StoryFilterRequest;
import org.sep490.backend.module.content.dto.request.StoryRequest;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.enums.ContentStatus;
import org.sep490.backend.module.content.mapper.StoryMapper;
import org.sep490.backend.module.content.repository.StoryRepository;
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

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE,  makeFinal = true)
public class StoryServiceImpl implements StoryService {

    StoryRepository storyRepository;
    StoryMapper storyMapper;
    UserService userService;

    @Override
    @Transactional
    public StoryResponse create(StoryRequest storyRequest) {
        Story story = storyMapper.toEntity(storyRequest);
        //story.setCreatedBy(userService.getCurrentUser());

        int index = storyRepository.countByHotspot_HotspotId(storyRequest.getHotspotId());
        if (index >= 0) {
            story.setOrderIndex(index + 1);
        }

        story  = storyRepository.save(story);
        return storyMapper.toResponse(story);
    }

    @Override
    @Transactional
    public StoryResponse update(Long id, StoryRequest storyRequest) {
        Story story = getById(id);
        storyMapper.updateFromRequest(story, storyRequest);
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
    public List<StoryResponse> getByHotspot(Long hotspotId) {
        return storyRepository.findByHotspot_HotspotId(hotspotId).stream()
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
        Story story = storyRepository.findById(id).orElseThrow(
                () -> new RuntimeException("Story not found with id: " + id)
        );
        return story;
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
}
