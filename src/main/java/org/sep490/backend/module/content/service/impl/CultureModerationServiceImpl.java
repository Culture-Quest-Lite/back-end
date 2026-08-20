package org.sep490.backend.module.content.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.config.redis.CacheNames;
import org.sep490.backend.module.content.dto.request.CultureRejectRequest;
import org.sep490.backend.module.content.dto.response.PendingCultureResponse;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.dto.response.TagResponse;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.sep490.backend.module.content.entity.enumeration.TagStatus;
import org.sep490.backend.module.content.mapper.StoryMapper;
import org.sep490.backend.module.content.mapper.TagMapper;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.content.repository.TagRepository;
import org.sep490.backend.module.content.service.inter.CultureModerationService;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CultureModerationServiceImpl implements CultureModerationService {

    TagRepository tagRepository;
    StoryRepository storyRepository;
    TagMapper tagMapper;
    StoryMapper storyMapper;
    UserService userService;

    @Override
    @Transactional(readOnly = true)
    public PendingCultureResponse getPending() {
        List<TagResponse> tags = tagRepository.findByTagStatusOrderByCreatedAtAsc(TagStatus.PENDING_REVIEW)
                .stream()
                .map(tagMapper::toResponse)
                .toList();
        List<StoryResponse> stories = storyRepository.findByStatus(ContentStatus.PENDING_REVIEW)
                .stream()
                .map(storyMapper::toResponse)
                .toList();
        return new PendingCultureResponse(tags, stories);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.TAGS, allEntries = true)
    public TagResponse approveTag(Long tagId) {
        Tag tag = getPendingTag(tagId);
        tag.setTagStatus(TagStatus.ACTIVE);
        tag.setRejectReason(null);
        tag.setModerateBy(currentUserId());
        tag.setModerateAt(LocalDateTime.now());
        return tagMapper.toResponse(tagRepository.save(tag));
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.TAGS, allEntries = true)
    public TagResponse rejectTag(Long tagId, CultureRejectRequest request) {
        Tag tag = getPendingTag(tagId);
        tag.setTagStatus(TagStatus.REJECTED);
        tag.setRejectReason(request.getRejectReason());
        tag.setModerateBy(currentUserId());
        tag.setModerateAt(LocalDateTime.now());
        return tagMapper.toResponse(tagRepository.save(tag));
    }

    @Override
    @Transactional
    public StoryResponse approveStory(Long storyId) {
        Story story = getPendingStory(storyId);
        story.setStatus(ContentStatus.DRAFT);
        story.setRejectReason(null);
        story.setModerateBy(currentUserId());
        story.setModerateAt(LocalDateTime.now());
        return storyMapper.toResponse(storyRepository.save(story));
    }

    @Override
    @Transactional
    public StoryResponse rejectStory(Long storyId, CultureRejectRequest request) {
        Story story = getPendingStory(storyId);
        story.setStatus(ContentStatus.REJECTED);
        story.setRejectReason(request.getRejectReason());
        story.setModerateBy(currentUserId());
        story.setModerateAt(LocalDateTime.now());
        return storyMapper.toResponse(storyRepository.save(story));
    }

    private Tag getPendingTag(Long tagId) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tag với id: " + tagId));
        if (tag.getTagStatus() != TagStatus.PENDING_REVIEW) {
            throw new BusinessException("Tag này không ở trạng thái chờ duyệt văn hóa");
        }
        return tag;
    }

    private Story getPendingStory(Long storyId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy story với id: " + storyId));
        if (story.getStatus() != ContentStatus.PENDING_REVIEW) {
            throw new BusinessException("Story này không ở trạng thái chờ duyệt văn hóa");
        }
        return story;
    }

    private Long currentUserId() {
        return userService.getCurrentUser().getUserId();
    }
}
