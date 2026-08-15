package org.sep490.backend.module.content.service.impl;

import org.sep490.backend.module.content.service.inter.GeoQueryService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.config.redis.CacheNames;
import org.sep490.backend.config.redis.RedisCircuitBreaker;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GeoQueryServiceImpl implements GeoQueryService {

    static int VN_PRECISION = 3;

    static int NEARBY_PRECISION = 3;

    /** Bán kính tìm kiếm tối đa: 50km, đủ cho mọi màn hình khám phá của app. */
    static double MAX_RADIUS_IN_METERS = 50_000d;

    HotspotRepository hotspotRepository;
    RedisTemplate<String, Object> redisTemplate;
    RedisCircuitBreaker circuitBreaker;

    @Override
    public boolean isLocationInVietnam(Double longitude, Double latitude) {
        if (longitude == null || latitude == null) {
            return false;
        }
        validateCoordinate(longitude, latitude);
        String key = CacheNames.KEY_GEO_VN
                + round(latitude, VN_PRECISION) + ":" + round(longitude, VN_PRECISION);

        Object cached = circuitBreaker.read("geo.vn.get",
                () -> redisTemplate.opsForValue().get(key), null);
        if (cached != null) {
            return Boolean.parseBoolean(cached.toString());
        }

        boolean result = hotspotRepository.isLocationInVietnam(longitude, latitude);
        circuitBreaker.write("geo.vn.set",
                () -> redisTemplate.opsForValue().set(key, String.valueOf(result),
                        CacheNames.TTL_GEO_STATIC));
        return result;
    }

    @Override
    public List<Hotspot> findNearby(double longitude, double latitude,
                                    double radiusInMeters, String status) {
        validateCoordinate(longitude, latitude);
        if (radiusInMeters <= 0) {
            throw new BusinessException("Bán kính tìm kiếm phải lớn hơn 0");
        }
        if (radiusInMeters > MAX_RADIUS_IN_METERS) {
            throw new BusinessException("Bán kính tìm kiếm không được vượt quá 50000 mét");
        }
        if (status == null || status.isBlank()) {
            throw new BusinessException("Trạng thái địa điểm không được để trống");
        }
        String key = CacheNames.KEY_GEO_NEARBY
                + round(latitude, NEARBY_PRECISION) + ":"
                + round(longitude, NEARBY_PRECISION) + ":"
                + (long) radiusInMeters + ":" + status;

        Object cached = circuitBreaker.read("geo.nearby.get",
                () -> redisTemplate.opsForValue().get(key), null);

        if (cached instanceof List<?> cachedIds && !cachedIds.isEmpty()) {
            List<Long> ids = cachedIds.stream()
                    .map(o -> Long.valueOf(o.toString()))
                    .toList();
            List<Hotspot> found = hotspotRepository.findAllById(ids);
            if (found.size() == ids.size()) {
                return reorder(found, ids);
            }
        }

        List<Hotspot> result = hotspotRepository.findNearbyHotspotsWithStatus(
                longitude, latitude, radiusInMeters, status);

        List<Object> ids = new ArrayList<>(result.size());
        result.forEach(h -> ids.add(String.valueOf(h.getHotspotId())));
        circuitBreaker.write("geo.nearby.set",
                () -> redisTemplate.opsForValue().set(key, ids, CacheNames.TTL_GEO_NEARBY));

        return result;
    }

    @Override
    public String parseGeoJsonToWkt(String geoJson) {
        try {
            return hotspotRepository.parseGeoJsonToWkt(geoJson);
        } catch (DataAccessException ex) {
            log.warn("GeoJSON ranh giới không hợp lệ: {}", ex.getMostSpecificCause().getMessage());
            throw new BusinessException("Ranh giới không đúng định dạng GeoJSON");
        }
    }

    @Override
    public String findBoundaryGeoJson(Long hotspotId) {
        return hotspotRepository.findBoundaryGeoJson(hotspotId);
    }

    @Override
    public void evictNearby() {
        circuitBreaker.write("geo.nearby.evict", () -> {
            var keys = redisTemplate.keys(CacheNames.KEY_GEO_NEARBY + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        });
    }

    private List<Hotspot> reorder(List<Hotspot> found, List<Long> orderedIds) {
        Map<Long, Hotspot> byId = new java.util.HashMap<>();
        found.forEach(h -> byId.put(h.getHotspotId(), h));
        List<Hotspot> ordered = new ArrayList<>(orderedIds.size());
        orderedIds.forEach(id -> {
            Hotspot h = byId.get(id);
            if (h != null) {
                ordered.add(h);
            }
        });
        return ordered;
    }

    private String round(double value, int precision) {
        return String.format("%." + precision + "f", value);
    }

    /** Chặn toạ độ nằm ngoài hệ WGS84 trước khi đụng tới Redis hoặc PostGIS. */
    private void validateCoordinate(double longitude, double latitude) {
        if (longitude < -180 || longitude > 180) {
            throw new BusinessException("Kinh độ phải nằm trong khoảng -180 đến 180");
        }
        if (latitude < -90 || latitude > 90) {
            throw new BusinessException("Vĩ độ phải nằm trong khoảng -90 đến 90");
        }
    }
}
