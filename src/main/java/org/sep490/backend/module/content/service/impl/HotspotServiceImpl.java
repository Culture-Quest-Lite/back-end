package org.sep490.backend.module.content.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.filter.dto.SearchRequest;
import org.sep490.backend.common.filter.specification.GenericSpecification;
import org.sep490.backend.module.content.dto.request.HotspotRequest;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.content.dto.response.MediaResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.sep490.backend.module.content.entity.enumeration.MediaTargetType;
import org.sep490.backend.module.content.mapper.HotspotMapper;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.RouteHotspotRepository;
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
import java.util.List;
import java.util.HashSet;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.content.repository.TagRepository;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE,  makeFinal = true)
public class HotspotServiceImpl implements HotspotService {

    HotspotRepository hotspotRepository;
    HotspotMapper hotspotMapper;
    UserService userService;
    RouteHotspotRepository routeHotspotRepository;
    TagRepository tagRepository;
    MediaService mediaService;

    @Override
    @Transactional
    public HotspotResponse create(HotspotRequest request) {

        if(!hotspotRepository.isLocationInVietnam(request.getLongitude(), request.getLatitude())) {
            throw new BusinessException("Tọa độ của Hotspot phải thuộc lãnh thổ Việt Nam");
        }

        if(request.getEndTime().isBefore(request.getStartTime())) {
            throw new BusinessException("Thời gian kết thúc không hợp lệ");
        }

        if(request.getEstimatedDurationMax() < request.getEstimatedDurationMin()) {
            throw new BusinessException("Thời gian tham quan dự kiến không hợp lệ");
        }

        Hotspot hotspot = hotspotMapper.toEntity(request);
        List<Tag> tags = tagRepository.findAllById(request.getTagIds());
        hotspot.setTags(new HashSet<>(tags));
        hotspot.setCreatedBy(userService.getCurrentUser());
        hotspot.setStatus(ContentStatus.DRAFT);
        hotspot = hotspotRepository.save(hotspot);

        HotspotResponse response = hotspotMapper.toResponse(hotspot);
        if (request.getFiles() != null && request.getFiles().length > 0) {
            try {
                List<MediaResponse> mediaResponses = mediaService.uploadAndSaveMedias(
                        request.getFiles(), MediaTargetType.HOTSPOT, hotspot.getHotspotId());
                response.setMedias(mediaResponses);
            } catch (IOException e) {
                throw new BusinessException("Lỗi tải lên media: " + e.getMessage());
            }
        }
        return response;
    }

    @Override
    @Transactional
    public HotspotResponse update(Long id, HotspotRequest request) {
        Hotspot hotspot = getById(id);
        hotspotMapper.updateFromRequest(hotspot, request);
        List<Tag> tags = tagRepository.findAllById(request.getTagIds());
        hotspot.setTags(new HashSet<>(tags));
        hotspot = hotspotRepository.save(hotspot);
        return hotspotMapper.toResponse(hotspot);
    }

    @Override
    @Transactional(readOnly = true)
    public HotspotResponse getDetail(Long id) {
        Hotspot hotspot = getById(id);
        return hotspotMapper.toResponse(hotspot);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HotspotResponse> getAll() {
        return hotspotRepository.findAll().stream()
                .map(hotspotMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Hotspot hotspot = getById(id);
        hotspot.setStatus(ContentStatus.DELETED);
        hotspotRepository.save(hotspot);
    }

    @Override
    @Transactional(readOnly = true)
    public Hotspot getById(Long id) {
        Hotspot hotspot = hotspotRepository.findById(id).orElseThrow(
                () -> new BusinessException("Không tìm thấy Hotspot")
        );
        return hotspot;
    }

    @Override
    public Page<HotspotResponse> filterHotspots(SearchRequest request) {
        Sort sort = Sort.by(Sort.Direction.fromString(request.getSortDirection()), request.getSortBy());
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);

        GenericSpecification<Hotspot> spec = new GenericSpecification<>(request);

        return hotspotRepository.findAll(spec, pageable).map(hotspotMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HotspotResponse> getNearbyHotspots(Double latitude, Double longitude, Double distanceInMeters) {

        if(latitude == null || longitude == null) {
            throw new BusinessException("Tung độ và hoành độ không được để trống");
        }

        if(distanceInMeters <= 0) {
            throw new BusinessException("Khoảng cách phải lớn hơn 0");
        }

        List<Hotspot> nearbies = hotspotRepository.findNearbyHotspots(longitude, latitude, distanceInMeters);

        return nearbies.stream()
                .map(hotspotMapper::toResponse)
                .toList();
    }

    @Override
    public List<HotspotResponse> getHotspotsByRouteId(Long routeId) {
        List<Hotspot> hotspots = routeHotspotRepository.findHotspotsByRouteIdOrderByIndexAsc(routeId);
        return hotspots.stream()
                .map(hotspotMapper::toResponse)
                .toList();
    }
}
