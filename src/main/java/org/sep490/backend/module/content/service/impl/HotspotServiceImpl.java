package org.sep490.backend.module.content.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.locationtech.jts.geom.Point;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.filter.dto.SearchRequest;
import org.sep490.backend.common.filter.specification.GenericSpecification;
import org.sep490.backend.module.content.dto.request.HotspotRequest;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.enums.ContentStatus;
import org.sep490.backend.module.content.mapper.HotspotMapper;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.RouteHotspotRepository;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE,  makeFinal = true)
public class HotspotServiceImpl implements HotspotService {

    HotspotRepository hotspotRepository;
    HotspotMapper hotspotMapper;
    UserService userService;
    RouteHotspotRepository routeHotspotRepository;

    @Override
    @Transactional
    public HotspotResponse create(HotspotRequest request) {

        if(!hotspotRepository.isLocationInVietnam(request.getLongitude(), request.getLatitude())) {
            throw new BusinessException("Hotspot location must be within Vietnam");
        }

        Hotspot hotspot = hotspotMapper.toEntity(request);
        hotspot.setCreatedBy(userService.getCurrentUser());
        hotspot = hotspotRepository.save(hotspot);
        return hotspotMapper.toResponse(hotspot);
    }

    @Override
    @Transactional
    public HotspotResponse update(Long id, HotspotRequest request) {
        Hotspot hotspot = getById(id);
        hotspotMapper.updateFromRequest(hotspot, request);
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
                () -> new BusinessException("Hotspot not found")
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
    public List<HotspotResponse> getNearbyHotspots(Long hotspotId, Double distanceInMeters) {

        Hotspot centerHotspot = hotspotRepository.findById(hotspotId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy Hotspot với ID: " + hotspotId));

        if(distanceInMeters <= 0) {
            throw new BusinessException("Khoảng cách phải lớn hơn 0");
        }

        Point centerPoint = centerHotspot.getLocation();
        if (centerPoint == null) {
            throw new BusinessException("Hotspot này chưa được cấu hình tọa độ.");
        }

        double lon = centerPoint.getX();
        double lat = centerPoint.getY();

        List<Hotspot> nearbies = hotspotRepository.findNearbyHotspots(lon, lat, distanceInMeters, hotspotId);

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
