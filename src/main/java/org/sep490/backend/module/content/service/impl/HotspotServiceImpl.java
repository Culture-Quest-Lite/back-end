package org.sep490.backend.module.content.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.dto.request.HotspotRequest;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.mapper.HotspotMapper;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE,  makeFinal = true)
public class HotspotServiceImpl implements HotspotService {

    HotspotRepository hotspotRepository;
    HotspotMapper hotspotMapper;

    @Override
    public HotspotResponse create(HotspotRequest request) {
        Hotspot hotspot = hotspotMapper.toEntity(request);
        hotspot = hotspotRepository.save(hotspot);
        return hotspotMapper.toResponse(hotspot);
    }

    @Override
    public HotspotResponse update(Long id, HotspotRequest request) {
        Hotspot hotspot = getById(id);
        hotspotMapper.updateFromRequest(hotspot, request);
        hotspot = hotspotRepository.save(hotspot);
        return hotspotMapper.toResponse(hotspot);
    }

    @Override
    public HotspotResponse getDetail(Long id) {
        Hotspot hotspot = getById(id);
        return hotspotMapper.toResponse(hotspot);
    }

    @Override
    public List<HotspotResponse> getAll() {
        return hotspotRepository.findAll().stream()
                .map(hotspotMapper::toResponse)
                .toList();
    }

    @Override
    public void delete(Long id) {
        Hotspot hotspot = getById(id);
        hotspotRepository.delete(hotspot);
    }

    @Override
    public Hotspot getById(Long id) {
        Hotspot hotspot = hotspotRepository.findById(id).orElseThrow(
                () -> new RuntimeException("Hotspot not found")
        );
        return hotspot;
    }
}
