package org.sep490.backend.module.content.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.config.redis.CacheNames;
import org.sep490.backend.module.content.dto.projection.TagUsageProjection;
import org.sep490.backend.module.content.dto.record.CultureCheckResult;
import org.sep490.backend.module.content.entity.enumeration.CultureDecision;
import org.sep490.backend.module.content.service.inter.CultureGuardService;
import org.sep490.backend.module.content.dto.response.TagUsageResponse;
import org.sep490.backend.module.content.entity.enumeration.TagStatus;
import org.sep490.backend.module.content.dto.filter.TagFilterRequest;
import org.sep490.backend.module.content.dto.request.TagRequest;
import org.sep490.backend.module.content.dto.response.TagResponse;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.content.entity.enumeration.TagUsageType;
import org.sep490.backend.module.content.mapper.TagMapper;
import org.sep490.backend.module.content.dto.projection.TagRouteCountProjection;
import org.sep490.backend.module.content.dto.projection.TagStoryCountProjection;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.sep490.backend.module.content.entity.enumeration.RouteStatus;
import org.sep490.backend.module.content.repository.RouteRepository;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.content.repository.TagRepository;
import org.sep490.backend.module.content.service.inter.ImageService;
import org.sep490.backend.module.content.service.inter.TagService;
import org.sep490.backend.module.content.specification.TagSpecification;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TagServiceImpl implements TagService {

    static final String IMAGE_FOLDER = "tags";

    TagRepository tagRepository;
    RouteRepository routeRepository;
    StoryRepository storyRepository;
    TagMapper tagMapper;
    ImageService imageService;
    CultureGuardService cultureGuardService;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.TAGS, allEntries = true)
    public TagResponse create(TagRequest request) {
        if (tagRepository.existsByTagNameIgnoreCase(request.getTagName())) {
            throw new BusinessException("Tag với tên \"" + request.getTagName() + "\" đã tồn tại");
        }
        CultureCheckResult culture = cultureGuardService.checkAndEnforce(
                CultureGuardService.KIND_TAG, request.getTagName(), request.getConfirmCultural());

        Tag tag = tagMapper.toEntity(request);
        tag.setTagStatus(culture.decision() == CultureDecision.REVIEW
                ? TagStatus.PENDING_REVIEW
                : TagStatus.ACTIVE);
        applyCulture(tag, culture);
        tag.setImageUrl(imageService.resolveImageUrl(
                null, request.getImageFile(), IMAGE_FOLDER));
        tag = tagRepository.save(tag);
        return tagMapper.toResponse(tag);
    }

    private void applyCulture(Tag tag, CultureCheckResult culture) {
        tag.setCultureScore(culture.score());
        tag.setCultureReason(culture.reason());
        tag.setCultureCheckedAt(LocalDateTime.now());
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.TAGS, allEntries = true)
    public TagResponse update(Long id, TagRequest request) {
        Tag tag = getById(id);
        if (tag.getTagStatus() == TagStatus.INACTIVE) {
            throw new BusinessException("Tag đang bị vô hiệu hóa, không thể cập nhật");
        }
        if (tagRepository.existsByTagNameIgnoreCaseAndTagIdNot(request.getTagName(), id)) {
            throw new BusinessException("Tag với tên \"" + request.getTagName() + "\" đã tồn tại");
        }
        CultureCheckResult culture = cultureGuardService.checkAndEnforce(
                CultureGuardService.KIND_TAG, request.getTagName(), request.getConfirmCultural());

        tag.setTagName(request.getTagName().trim());
        // Tag từng bị admin từ chối thì dù bộ lọc PASS vẫn phải để admin duyệt lại,
        // vì lý do từ chối có thể nằm ngoài tầm nhìn của bộ lọc.
        if (culture.decision() == CultureDecision.REVIEW || tag.getTagStatus() == TagStatus.REJECTED) {
            tag.setTagStatus(TagStatus.PENDING_REVIEW);
            tag.setRejectReason(null);
        }
        applyCulture(tag, culture);
        tag.setImageUrl(imageService.resolveImageUrl(
                tag.getImageUrl(), request.getImageFile(), IMAGE_FOLDER));
        tag = tagRepository.save(tag);
        return tagMapper.toResponse(tag);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheNames.TAGS, key = "#id")
    public TagResponse getDetail(Long id) {
        List<Long> tagIds = new ArrayList<>();
        List<TagUsageResponse> tagUsageResponses = new ArrayList<>();
        tagIds.add(id);

        TagResponse response = tagMapper.toResponse(getById(id));
        response.setRouteCount(routeRepository.countByTag_TagIdAndStatusNot(id, RouteStatus.DELETED));
        response.setStoryCount(storyRepository.countByTag_TagIdAndStatusNot(id, ContentStatus.DELETED));
        response.setHotspotCount(storyRepository.countDistinctHotspotsByTagId(id, ContentStatus.DELETED));

        List<TagUsageProjection> routeUsages = routeRepository.findRouteUsagesByTagIds(tagIds, RouteStatus.PUBLISHED);
        List<TagUsageProjection> storyUsages = storyRepository.findStoryUsagesByTagIds(tagIds, ContentStatus.PUBLISHED);

        for (TagUsageProjection routeUsage : routeUsages) {
            TagUsageResponse routeResponse = new TagUsageResponse();
            routeResponse.setRefId(routeUsage.getRefId());
            routeResponse.setType(TagUsageType.ROUTE);
            tagUsageResponses.add(routeResponse);
        }
        for (TagUsageProjection storyUsage : storyUsages) {
            TagUsageResponse storyResponse = new TagUsageResponse();
            storyResponse.setRefId(storyUsage.getRefId());
            storyResponse.setType(TagUsageType.STORY);
            tagUsageResponses.add(storyResponse);
        }

        response.setUsages(tagUsageResponses);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TagResponse> getAllWithFilter(TagFilterRequest filter) {
        Sort sort = filter.getSortDir().equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(filter.getSortBy()).ascending()
                : Sort.by(filter.getSortBy()).descending();
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);
        Specification<Tag> spec = TagSpecification.filterTags(filter.getSearch(), filter.getStatus());
        Page<TagResponse> page = tagRepository.findAll(spec, pageable).map(tagMapper::toResponse);

        List<Long> tagIds = page.getContent().stream().map(TagResponse::getTagId).toList();
        if (!tagIds.isEmpty()) {
            Map<Long, Long> routeCounts = routeRepository.countRoutesByTagIds(tagIds, RouteStatus.DELETED)
                    .stream()
                    .collect(Collectors.toMap(TagRouteCountProjection::getTagId, TagRouteCountProjection::getRouteCount));
            Map<Long, TagStoryCountProjection> storyCounts = storyRepository.countStoriesAndHotspotsByTagIds(tagIds, ContentStatus.DELETED)
                    .stream()
                    .collect(Collectors.toMap(TagStoryCountProjection::getTagId, p -> p));
            page.getContent().forEach(t -> {
                t.setRouteCount(routeCounts.getOrDefault(t.getTagId(), 0L));
                TagStoryCountProjection sc = storyCounts.get(t.getTagId());
                t.setStoryCount(sc != null ? sc.getStoryCount() : 0L);
                t.setHotspotCount(sc != null ? sc.getHotspotCount() : 0L);
            });
        }
        return page;
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.TAGS, allEntries = true)
    public void delete(Long id) {
        Tag tag = getById(id);
        if (tag.getTagStatus() == TagStatus.DELETED) {
            throw new BusinessException("Tag đã bị xóa trước đó");
        }
        tag.setTagStatus(TagStatus.DELETED);
        tagRepository.save(tag);
    }

    @Override
    public Tag getById(Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy tag với id: " + id));
        if (tag.getTagStatus() == TagStatus.DELETED) {
            throw new BusinessException("Tag với id " + id + " đã bị xóa");
        }
        return tag;
    }

    @Override
    public Page<TagResponse> searchUsage(TagFilterRequest filter) {
        Sort sort = filter.getSortDir().equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(filter.getSortBy()).ascending()
                : Sort.by(filter.getSortBy()).descending();
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);
        Specification<Tag> spec = TagSpecification.filterTags(filter.getSearch(), filter.getStatus());
        Page<TagResponse> page = tagRepository.findAll(spec, pageable).map(tagMapper::toResponse);

        List<Long> tagIds = page.getContent().stream().map(TagResponse::getTagId).toList();
        if (!tagIds.isEmpty()) {
            Map<Long, Long> routeCounts = routeRepository.countRoutesByTagIds(tagIds, RouteStatus.DELETED)
                    .stream()
                    .collect(Collectors.toMap(TagRouteCountProjection::getTagId, TagRouteCountProjection::getRouteCount));
            Map<Long, TagStoryCountProjection> storyCounts = storyRepository.countStoriesAndHotspotsByTagIds(tagIds, ContentStatus.DELETED)
                    .stream()
                    .collect(Collectors.toMap(TagStoryCountProjection::getTagId, p -> p));

            List<TagUsageProjection> routeUsages = routeRepository.findRouteUsagesByTagIds(tagIds, RouteStatus.PUBLISHED);
            List<TagUsageProjection> storyUsages = storyRepository.findStoryUsagesByTagIds(tagIds, ContentStatus.PUBLISHED);

            Map<Long, List<TagUsageResponse>> usagesByTagId = new HashMap<>();
            for (Long tagId : tagIds) {
                usagesByTagId.put(tagId, new ArrayList<>());
            }

            routeUsages.forEach(r -> {
                TagUsageResponse usage = new TagUsageResponse();
                usage.setRefId(r.getRefId());
                usage.setType(TagUsageType.ROUTE);
                usagesByTagId.get(r.getTagId()).add(usage);
            });

            storyUsages.forEach(s -> {
                TagUsageResponse usage = new TagUsageResponse();
                usage.setRefId(s.getRefId());
                usage.setType(TagUsageType.STORY);
                usagesByTagId.get(s.getTagId()).add(usage);
            });

            page.getContent().forEach(t -> {
                t.setRouteCount(routeCounts.getOrDefault(t.getTagId(), 0L));
                TagStoryCountProjection sc = storyCounts.get(t.getTagId());
                t.setStoryCount(sc != null ? sc.getStoryCount() : 0L);
                t.setHotspotCount(sc != null ? sc.getHotspotCount() : 0L);

                t.setUsages(usagesByTagId.getOrDefault(t.getTagId(), new ArrayList<>()));
            });
        }

        return page;
    }
}

