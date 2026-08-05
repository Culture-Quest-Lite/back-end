package org.sep490.backend.module.content.service.impl;

import org.sep490.backend.module.content.service.inter.GeoQueryService;

import org.sep490.backend.module.content.service.inter.CheckInStatusService;

import org.sep490.backend.module.content.service.inter.RatingSummaryService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.filter.dto.SearchRequest;
import org.sep490.backend.common.filter.specification.GenericSpecification;
import org.sep490.backend.module.content.dto.request.HotspotRequest;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.content.dto.response.MediaResponse;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.dto.response.TagResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.sep490.backend.module.content.entity.enumeration.MediaTargetType;
import org.sep490.backend.module.content.mapper.HotspotMapper;
import org.sep490.backend.module.content.mapper.StoryMapper;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.content.service.inter.MediaService;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class HotspotServiceImpl implements HotspotService {

    HotspotRepository hotspotRepository;
    HotspotMapper hotspotMapper;
    UserService userService;
    StoryRepository storyRepository;
    StoryMapper storyMapper;
    MediaService mediaService;
    RatingSummaryService ratingSummaryService;
    CheckInStatusService checkInStatusService;
    GeoQueryService geoQueryService;

    @Override
    @Transactional
    public HotspotResponse create(HotspotRequest request) {

        if (!geoQueryService.isLocationInVietnam(request.getLongitude(), request.getLatitude())) {
            throw new BusinessException("Tọa độ của Hotspot phải thuộc lãnh thổ Việt Nam");
        }

        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new BusinessException("Thời gian kết thúc không hợp lệ");
        }

        if (request.getEstimatedDurationMax() < request.getEstimatedDurationMin()) {
            throw new BusinessException("Thời gian tham quan dự kiến không hợp lệ");
        }

        Hotspot hotspot = hotspotMapper.toEntity(request);
        hotspot.setCreatedBy(userService.getCurrentUser());
        hotspot.setStatus(ContentStatus.DRAFT);
        hotspot = hotspotRepository.save(hotspot);
        geoQueryService.evictNearby();

        //assignStoriesToHotspot(hotspot, request.getStoryIds());

        HotspotResponse response = buildHotspotResponse(hotspot);
        if (request.getFiles() != null && request.getFiles().length > 0) {
            try {
                List<MediaResponse> mediaResponses = mediaService.uploadAndSaveMedias(
                        request.getFiles(), MediaTargetType.HOTSPOT, hotspot.getHotspotId());
                response.setMedias(mediaResponses);
            } catch (IOException e) {
                throw new BusinessException("Lỗi tải lên media: " + e.getMessage());
            }
        }
        return applyRatingSummary(response);
    }

    @Override
    @Transactional
    public HotspotResponse update(Long id, HotspotRequest request) {
        Hotspot hotspot = getById(id);
        hotspotMapper.updateFromRequest(hotspot, request);
        hotspot = hotspotRepository.save(hotspot);
        geoQueryService.evictNearby();

        //unsetStoriesFromHotspot(hotspot.getHotspotId());
        //assignStoriesToHotspot(hotspot, request.getStoryIds());

        if (request.getFiles() != null && request.getFiles().length > 0) {
            try {
                List<MediaResponse> mediaResponses = mediaService.uploadAndSaveMedias(
                        request.getFiles(), MediaTargetType.HOTSPOT, hotspot.getHotspotId());
                // response.setMedias(mediaResponses);
            } catch (IOException e) {
                throw new BusinessException("Lỗi tải lên media: " + e.getMessage());
            }
        }
        return applyRatingSummary(buildHotspotResponse(hotspot));
    }

    @Override
    @Transactional
    public HotspotResponse updateStatus(Long id, ContentStatus status) {
        Hotspot hotspot = getById(id);
        hotspot.setStatus(status);
        hotspot = hotspotRepository.save(hotspot);
        return applyRatingSummary(buildHotspotResponse(hotspot));
    }

    @Override
    @Transactional(readOnly = true)
    public HotspotResponse getDetail(Long id) {
        Hotspot hotspot = getById(id);
        return applyRatingSummary(buildHotspotResponse(hotspot));
    }

    @Override
    @Transactional(readOnly = true)
    public List<HotspotResponse> getAll() {
        List<HotspotResponse> responses = hotspotRepository.findAll().stream()
                .map(this::buildHotspotResponse)
                .toList();
        applyRatingSummary(responses);
        return responses;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Hotspot hotspot = getById(id);
        hotspot.setStatus(ContentStatus.DELETED);
        hotspotRepository.save(hotspot);
        geoQueryService.evictNearby();
    }

    @Override
    @Transactional(readOnly = true)
    public Hotspot getById(Long id) {
        return hotspotRepository.findById(id).orElseThrow(
                () -> new BusinessException("Không tìm thấy Hotspot")
        );
    }

    @Override
    public Page<HotspotResponse> filterHotspots(SearchRequest request) {
        Sort sort = Sort.by(Sort.Direction.fromString(request.getSortDirection()), request.getSortBy());
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);

        GenericSpecification<Hotspot> spec = new GenericSpecification<>(request);

        Page<HotspotResponse> page = hotspotRepository.findAll(spec, pageable).map(this::buildHotspotResponse);
        applyRatingSummary(page.getContent());
        return page;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HotspotResponse> getNearbyHotspots(Double latitude, Double longitude, Double distanceInMeters) {

        if (latitude == null || longitude == null) {
            throw new BusinessException("Tung độ và hoành độ không được để trống");
        }

        if (distanceInMeters <= 0) {
            throw new BusinessException("Khoảng cách phải lớn hơn 0");
        }

        // Qua cache: ST_DWithin mất ~118ms mỗi lần gọi
        List<Hotspot> nearbies = geoQueryService.findNearby(
                longitude, latitude, distanceInMeters, ContentStatus.PUBLISHED.name());

        List<HotspotResponse> responses = nearbies.stream()
                .map(this::buildHotspotResponse)
                .toList();
        applyRatingSummary(responses);
        return responses;
    }

    private HotspotResponse applyRatingSummary(HotspotResponse response) {
        return ratingSummaryService.applyToHotspot(response);
    }

    private void applyRatingSummary(List<HotspotResponse> responses) {
        ratingSummaryService.applyToHotspots(responses);
    }

    @Override
    public List<HotspotResponse> getHotspotsByRouteId(Long routeId) {
        List<Hotspot> hotspots = storyRepository.findHotspotsByRouteIdOrderByIndexAsc(routeId);
        List<HotspotResponse> responses = hotspots.stream()
                .map(this::buildHotspotResponse)
                .toList();
        applyRatingSummary(responses);
        return responses;
    }

    private HotspotResponse buildHotspotResponse(Hotspot hotspot) {
        HotspotResponse response = hotspotMapper.toResponse(hotspot);

        checkInStatusService.apply(response);

        List<StoryResponse> storyResponses = storyRepository
                .findByHotspotOrderedByIndex(hotspot.getHotspotId())
                .stream()
                .map(storyMapper::toResponse)
                .toList();
        ratingSummaryService.applyToStories(storyResponses);
        response.setStories(storyResponses);
        return response;
    }

    private void assignStoriesToHotspot(Hotspot hotspot, List<Long> storyIds) {
        if (storyIds == null || storyIds.isEmpty()) return;

        for (int i = 0; i < storyIds.size(); i++) {
            Long storyId = storyIds.get(i);
            Story story = storyRepository.findById(storyId)
                    .orElseThrow(() -> new BusinessException("Story không tồn tại với ID: " + storyId));

            if (story.getHotspot() != null && !story.getHotspot().getHotspotId().equals(hotspot.getHotspotId())) {
                throw new BusinessException("Story ID " + storyId + " đã thuộc hotspot khác");
            }

            story.setHotspot(hotspot);
            story.setOrderIndex(i + 1);
            storyRepository.save(story);
        }
    }

    private void unsetStoriesFromHotspot(Long hotspotId) {
        List<Story> stories = storyRepository.findByHotspotOrderedByIndex(hotspotId);
        for (Story s : stories) {
            s.setHotspot(null);
            s.setOrderIndex(null);
        }
        storyRepository.saveAll(stories);
    }
}
