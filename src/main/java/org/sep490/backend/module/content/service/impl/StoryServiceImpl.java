package org.sep490.backend.module.content.service.impl;

import org.sep490.backend.module.content.service.inter.RatingSummaryService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.content.dto.filter.StoryFilterRequest;
import org.sep490.backend.module.content.dto.record.CultureCheckResult;
import org.sep490.backend.module.content.dto.request.StoryRequest;
import org.sep490.backend.module.content.dto.response.MediaResponse;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.sep490.backend.module.content.entity.enumeration.CultureDecision;
import org.sep490.backend.module.content.entity.enumeration.MediaTargetType;
import org.sep490.backend.module.content.mapper.StoryMapper;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.content.repository.TagRepository;
import org.sep490.backend.module.content.service.inter.CultureGuardService;
import org.sep490.backend.module.content.service.inter.HotspotService;
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
import java.time.LocalDateTime;
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
    HotspotService hotspotService;
    RatingSummaryService ratingSummaryService;
    CultureGuardService cultureGuardService;

    @Override
    @Transactional
    public StoryResponse create(StoryRequest storyRequest) {
        Tag tag = tagRepository.findById(storyRequest.getTagId())
                .orElseThrow(() -> new BusinessException("Tag không tồn tại với ID: " + storyRequest.getTagId()));

        Hotspot hotspot = hotspotService.getById(storyRequest.getHotspotId());

        CultureCheckResult culture = cultureGuardService.checkAndEnforce(
                CultureGuardService.KIND_STORY, buildCultureText(storyRequest), storyRequest.getConfirmCultural());

        Story story = storyMapper.toEntity(storyRequest);
        story.setCreatedBy(userService.getCurrentUser());
        story.setTag(tag);
        story.setHotspot(hotspot);
        story.setStatus(culture.decision() == CultureDecision.REVIEW
                ? ContentStatus.PENDING_REVIEW
                : ContentStatus.DRAFT);
        applyCulture(story, culture);

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
        return ratingSummaryService.applyToStory(response);
    }

    @Override
    @Transactional
    public StoryResponse update(Long id, StoryRequest storyRequest) {
        Story story = getById(id);

        Tag tag = tagRepository.findById(storyRequest.getTagId())
                .orElseThrow(() -> new BusinessException("Tag không tồn tại với ID: " + storyRequest.getTagId()));

        Hotspot hotspot = hotspotService.getById(storyRequest.getHotspotId());

        CultureCheckResult culture = cultureGuardService.checkAndEnforce(
                CultureGuardService.KIND_STORY, buildCultureText(storyRequest), storyRequest.getConfirmCultural());

        storyMapper.updateFromRequest(story, storyRequest);
        story.setTag(tag);
        story.setHotspot(hotspot);
        // Story từng bị admin từ chối thì dù bộ lọc PASS vẫn phải để admin duyệt lại.
        if (culture.decision() == CultureDecision.REVIEW || story.getStatus() == ContentStatus.REJECTED) {
            story.setStatus(ContentStatus.PENDING_REVIEW);
            story.setRejectReason(null);
        }
        applyCulture(story, culture);

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
        return ratingSummaryService.applyToStory(response);
    }

    private static String buildCultureText(StoryRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.getTitle() != null) {
            sb.append(request.getTitle()).append('\n');
        }
        if (request.getContent() != null) {
            sb.append(request.getContent()).append('\n');
        }
        if (request.getAudioScript() != null) {
            sb.append(request.getAudioScript());
        }
        return sb.toString();
    }

    private static void applyCulture(Story story, CultureCheckResult culture) {
        story.setCultureScore(culture.score());
        story.setCultureReason(culture.reason());
        story.setCultureCheckedAt(LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public StoryResponse getDetail(Long id) {
        return ratingSummaryService.applyToStory(storyMapper.toResponse(getById(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoryResponse> getByHotspot(Long hotspotId, Long routeId) {
        List<Story> stories;
//        if (routeId != null) {
//            List<Long> routeTagIds = storyRepository.findTagIdsByRouteId(routeId);
//            if (!routeTagIds.isEmpty()) {
//                stories = storyRepository.findByHotspotOrderedByRouteTag(hotspotId, routeTagIds);
//            } else {
//                stories = storyRepository.findByHotspotOrderedByIndex(hotspotId);
//            }
//        } else {
//            stories = storyRepository.findByHotspotOrderedByIndex(hotspotId);
//        }

        if (hotspotId == null && routeId == null) {
            throw new BusinessException("Cần cung cấp ít nhất một trong hai tham số: hotspotId hoặc routeId");
        } else if (hotspotId == null) {
            stories = storyRepository.findByRoute_RouteIdAndStatus(routeId, ContentStatus.PUBLISHED);
        } else if (routeId == null) {
            stories = storyRepository.findByHotspot_HotspotIdAndStatus(hotspotId, ContentStatus.PUBLISHED);
        } else {
            stories = storyRepository.findByRoute_RouteIdAndHotspot_HotspotIdAndStatus(routeId, hotspotId, ContentStatus.PUBLISHED);
        }

        List<StoryResponse> responses = stories.stream()
                .map(storyMapper::toResponse)
                .toList();
        ratingSummaryService.applyToStories(responses);
        return responses;
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

        Page<StoryResponse> page = storyRepository.findAll(spec, pageable).map(storyMapper::toResponse);
        ratingSummaryService.applyToStories(page.getContent());
        return page;
    }

    @Override
    @Transactional
    public StoryResponse updateStatus(Long id, ContentStatus status) {

        Story story = getById(id);
        validateStatusTransition(story.getStatus(), status);
        story.setStatus(status);

        storyRepository.save(story);

        return ratingSummaryService.applyToStory(storyMapper.toResponse(story));
    }

    /**
     * PENDING_REVIEW và REJECTED là kết quả của bộ lọc văn hóa và kiểm duyệt viên,
     * không được đặt hay gỡ bằng tay. Nếu không chặn ở đây thì chỉ cần gọi
     * PUT /{id}/status?status=PUBLISHED là lách được toàn bộ khâu kiểm duyệt.
     */
    private static void validateStatusTransition(ContentStatus current, ContentStatus target) {
        if (target == ContentStatus.PENDING_REVIEW || target == ContentStatus.REJECTED) {
            throw new BusinessException(
                    "Không thể tự đặt trạng thái {}. Trạng thái này do bộ lọc văn hóa và kiểm duyệt viên quyết định.",
                    target);
        }
        if (current == ContentStatus.PENDING_REVIEW) {
            throw new BusinessException(
                    "Câu chuyện đang chờ kiểm duyệt viên duyệt, không thể đổi trạng thái");
        }
        if (current == ContentStatus.REJECTED) {
            throw new BusinessException(
                    "Câu chuyện đã bị từ chối. Hãy sửa nội dung và lưu lại để gửi kiểm duyệt viên duyệt lại.");
        }
    }
}
