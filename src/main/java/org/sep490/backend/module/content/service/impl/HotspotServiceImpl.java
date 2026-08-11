package org.sep490.backend.module.content.service.impl;

import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.repository.RouteRepository;
import org.sep490.backend.module.content.service.inter.GeoQueryService;

import org.sep490.backend.module.content.service.inter.CheckInStatusService;

import org.sep490.backend.module.content.service.inter.RatingSummaryService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.utils.SpatialUtils;
import org.sep490.backend.module.exploration.service.impl.CheckInPolicy;
import org.sep490.backend.module.user.entity.enumeration.UserRole;
import org.springframework.util.StringUtils;
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
import java.util.ArrayList;
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
    RouteRepository routeRepository;

    @Override
    @Transactional
    public HotspotResponse create(HotspotRequest request) {

        validateHotspotRequest(request);

        Hotspot hotspot = hotspotMapper.toEntity(request);
        applyCheckInZone(hotspot, request);
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
        // Trước đây update không validate gì cả nên có thể sửa toạ độ ra ngoài Việt Nam.
        validateHotspotRequest(request);
        hotspotMapper.updateFromRequest(hotspot, request);
        applyCheckInZone(hotspot, request);
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
        HotspotResponse response = buildHotspotResponse(hotspot);
        // Chỉ đổ ranh giới ở màn chi tiết; list/filter bỏ qua để tránh N+1.
        if (hotspot.getBoundary() != null) {
            response.setBoundaryGeoJson(geoQueryService.findBoundaryGeoJson(id));
        }
        return applyRatingSummary(response);
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

        User curator = userService.getCurrentUser();

        if(!curator.getRole().equals(UserRole.CURATOR)) {
            throw new BusinessException("Bạn không có quyền xóa Hotspot");
        }

        Hotspot hotspot = getById(id);

        if(!hotspot.getCreatedBy().equals(curator)) {
            throw new BusinessException("Bạn không có quyền xóa Hotspot này");
        }

        List<Story> stories = storyRepository.findByHotspot_HotspotIdAndStatus(hotspot.getHotspotId(), ContentStatus.PUBLISHED);
        List<Long> publishedIds = new ArrayList<>();

        for(Story story : stories) {
            if(story.getStatus().equals(ContentStatus.PUBLISHED)) {
                publishedIds.add(story.getStoryId());
            }
        }

        if(!publishedIds.isEmpty()) {
            throw new BusinessException("Không thể xóa Hotspot này vì có các Story đã được xuất bản: " + publishedIds);
        }

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

    private void validateHotspotRequest(HotspotRequest request) {
        if (!geoQueryService.isLocationInVietnam(request.getLongitude(), request.getLatitude())) {
            throw new BusinessException("Tọa độ của Hotspot phải thuộc lãnh thổ Việt Nam");
        }

        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new BusinessException("Thời gian kết thúc không hợp lệ");
        }

        if (request.getEstimatedDurationMax() < request.getEstimatedDurationMin()) {
            throw new BusinessException("Thời gian tham quan dự kiến không hợp lệ");
        }
    }

    private void applyCheckInZone(Hotspot hotspot, HotspotRequest request) {
        if (request.getCheckInRadius() != null) {
            hotspot.setCheckInRadius(request.getCheckInRadius());
        } else if (hotspot.getCheckInRadius() == null) {
            hotspot.setCheckInRadius(CheckInPolicy.DEFAULT_RADIUS_METERS);
        }

        if (!StringUtils.hasText(request.getBoundaryGeoJson())) {
            hotspot.setBoundary(null);
            return;
        }

        String wkt = geoQueryService.parseGeoJsonToWkt(request.getBoundaryGeoJson());
        if (!StringUtils.hasText(wkt)) {
            throw new BusinessException("Ranh giới không đúng định dạng GeoJSON");
        }

        Geometry geometry;
        try {
            geometry = new WKTReader().read(wkt);
        } catch (ParseException e) {
            throw new BusinessException("Ranh giới không đúng định dạng GeoJSON");
        }

        if (!(geometry instanceof Polygon polygon)) {
            throw new BusinessException("Ranh giới phải là một vùng khép kín (Polygon)");
        }

        if (!polygon.isValid()) {
            throw new BusinessException("Ranh giới bị tự cắt, vui lòng vẽ lại");
        }

        Point center = SpatialUtils.fromCoordinates(request.getLongitude(), request.getLatitude());
        if (center == null || !polygon.covers(center)) {
            throw new BusinessException("Toạ độ hotspot phải nằm trong ranh giới đã vẽ");
        }

        polygon.setSRID(4326);
        hotspot.setBoundary(polygon);
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

    @Override
    public List<Hotspot> getHotspotByRouteId(Long routeId) {

        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy Route với ID: " + routeId));

        List<Hotspot> hotspots = new ArrayList<>();
        List<Story> stories = storyRepository.findByRoute_RouteIdAndStatus(route.getRouteId(), ContentStatus.PUBLISHED);
        for (Story story : stories) {
            hotspots.add(story.getHotspot());
        }
        return hotspots;
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
