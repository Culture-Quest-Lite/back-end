package org.sep490.backend.module.planner.service.impl;

import org.sep490.backend.module.content.service.inter.GeoQueryService;

import org.sep490.backend.module.content.service.inter.CheckInStatusService;

import org.sep490.backend.module.content.service.inter.RatingSummaryService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
import org.sep490.backend.common.utils.SpatialUtils;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.sep490.backend.module.content.mapper.HotspotMapper;
import org.sep490.backend.module.content.mapper.StoryMapper;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.planner.dto.record.HotspotPick;
import org.sep490.backend.module.planner.dto.record.HotspotPickList;
import org.sep490.backend.module.planner.dto.request.DescriptionSuggestRequest;
import org.sep490.backend.module.planner.dto.request.NearbySuggestRequest;
import org.sep490.backend.module.planner.dto.response.HotspotSuggestionResponse;
import org.sep490.backend.config.redis.CacheNames;
import org.sep490.backend.config.redis.RedisCircuitBreaker;
import org.sep490.backend.module.planner.service.AISuggestionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AISuggestionServiceImpl implements AISuggestionService {
    static double DEFAULT_DESC_RADIUS = 5000;
    static double DEFAULT_NEARBY_RADIUS = 3000;
    static int DEFAULT_LIMIT = 10;
    static int MAX_CANDIDATES = 40; // giới hạn để prompt gọn

    ChatClient chatClient;
    HotspotRepository hotspotRepository;
    StoryRepository storyRepository;
    HotspotService hotspotService;
    HotspotMapper hotspotMapper;
    StoryMapper storyMapper;
    RatingSummaryService ratingSummaryService;
    CheckInStatusService checkInStatusService;
    RedisTemplate<String, Object> redisTemplate;
    RedisCircuitBreaker circuitBreaker;
    GeoQueryService geoQueryService;

    @Override
    @Transactional(readOnly = true)
    public List<HotspotSuggestionResponse> suggestByDescription(DescriptionSuggestRequest request) {
        int limit = request.getLimit() != null ? request.getLimit() : DEFAULT_LIMIT;
        double radius = request.getRadiusInMeters() != null ? request.getRadiusInMeters() : DEFAULT_DESC_RADIUS;

        List<Hotspot> candidates = retrieveCandidates(request, radius);
        if (candidates.isEmpty()) return List.of();

        Map<Long, Hotspot> byId = new LinkedHashMap<>();
        candidates.forEach(h -> byId.put(h.getHotspotId(), h));

        HotspotPickList picks = rerankWithLlm(request.getDescription(), candidates);
        if (picks == null || picks.picks() == null) {
            return List.of();
        }

        List<Point> origins = resolveOrigins(request);

        List<HotspotSuggestionResponse> result = new ArrayList<>();
        for (HotspotPick pick : picks.picks()) {
            Hotspot h = byId.get(pick.hotspotId());
            if (h == null) continue;
            result.add(HotspotSuggestionResponse.builder()
                    .hotspot(toResponseWithStories(h))
                    .score(pick.score())
                    .reason(pick.reason())
                    .distanceInMeters(distanceToNearestOrigin(origins, h))
                    .build());
        }
        result.sort(Comparator.comparing(HotspotSuggestionResponse::getScore,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result.stream().limit(limit).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HotspotSuggestionResponse> suggestNearby(NearbySuggestRequest request) {
        int limit = request.getLimit() != null ? request.getLimit() : DEFAULT_LIMIT;
        double radius = request.getRadiusInMeters() != null ? request.getRadiusInMeters() : DEFAULT_NEARBY_RADIUS;

        List<Long> anchors = request.getAnchorHotspotIds();
        Map<Long, HotspotSuggestionResponse> map = new HashMap<>();

        for (Long anchorId : anchors) {
            Hotspot anchor = hotspotService.getById(anchorId);
            // Qua cache: ST_DWithin ~118ms, mà đây là vòng lặp theo từng anchor
            List<Hotspot> nearBy = geoQueryService.findNearby(
                    anchor.getLocation().getX(),
                    anchor.getLocation().getY(),
                    radius,
                    ContentStatus.PUBLISHED.name()
            );

            for (Hotspot h : nearBy) {
                if (anchors.contains(h.getHotspotId())) continue;
                if (h.getStatus() != ContentStatus.PUBLISHED) continue;
                double dist = SpatialUtils.calculateDistanceInMeters(anchor.getLocation(), h.getLocation());
                HotspotSuggestionResponse existing = map.get(h.getHotspotId());
                if (existing == null || dist < existing.getDistanceInMeters()) {
                    map.put(h.getHotspotId(), HotspotSuggestionResponse.builder()
                                    .hotspot(toResponseWithStories(h))
                                    .distanceInMeters(dist)
                                    .build());
                }
            }
        }
        return map.values().stream()
                .sorted(Comparator.comparing(HotspotSuggestionResponse::getDistanceInMeters))
                .limit(limit)
                .toList();
    }

    private String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private HotspotResponse toResponseWithStories(Hotspot hotspot) {
        HotspotResponse response = hotspotMapper.toResponse(hotspot);
        List<StoryResponse> stories = storyRepository
                .findByHotspotOrderedByIndex(hotspot.getHotspotId())
                .stream()
                .map(storyMapper::toResponse)
                .toList();
        ratingSummaryService.applyToStories(stories);
        response.setStories(stories);
        checkInStatusService.apply(response);
        return ratingSummaryService.applyToHotspot(response);
    }

    private List<Point> resolveOrigins(DescriptionSuggestRequest request) {
        if (request.getLatitude() != null && request.getLongitude() != null) {
            return List.of(SpatialUtils.fromCoordinates(request.getLongitude(), request.getLatitude()));
        }
        List<Long> anchors = request.getAnchorHotspotIds();
        if (anchors != null && !anchors.isEmpty()) {
            return anchors.stream()
                    .map(id -> hotspotService.getById(id).getLocation())
                    .toList();
        }
        return List.of();
    }

    private Double distanceToNearestOrigin(List<Point> origins, Hotspot hotspot) {
        if (origins.isEmpty()) return null;
        return origins.stream()
                .mapToDouble(o -> SpatialUtils.calculateDistanceInMeters(o, hotspot.getLocation()))
                .min()
                .orElse(0.0);
    }

    private List<Hotspot> retrieveCandidates(DescriptionSuggestRequest request, double radius) {
        Map<Long, Hotspot> map = new LinkedHashMap<>();
        List<Long> anchors = request.getAnchorHotspotIds();

        if (anchors != null && !anchors.isEmpty()) {
            for (Long anchorId : anchors) {
                Hotspot anchor = hotspotService.getById(anchorId);
                geoQueryService.findNearby(
                        anchor.getLocation().getX(),
                        anchor.getLocation().getY(),
                        radius,
                        ContentStatus.PUBLISHED.name()
                ).forEach(h -> map.putIfAbsent(h.getHotspotId(), h));
            }
        } else if (request.getLatitude() != null && request.getLongitude() != null) {
            hotspotRepository.findNearbyHotspotsWithStatus(request.getLongitude(), request.getLatitude(), radius, ContentStatus.PUBLISHED.name())
                    .forEach(h -> map.putIfAbsent(h.getHotspotId(), h));
        } else {
            hotspotRepository.findByStatus(ContentStatus.PUBLISHED)
                    .forEach(h -> map.putIfAbsent(h.getHotspotId(), h));
        }

        if (anchors != null) anchors.forEach(map::remove);
        return map.values().stream()
                .filter(h -> h.getStatus() == ContentStatus.PUBLISHED)
                .limit(MAX_CANDIDATES)
                .toList();
    }

    private HotspotPickList rerankWithLlm(String description, List<Hotspot> candidates) {
        String cacheKey = buildRerankKey(description, candidates);

        Object cached = circuitBreaker.read("ai.get",
                () -> redisTemplate.opsForValue().get(cacheKey), null);
        if (cached instanceof HotspotPickList hit) {
            return hit;
        }

        HotspotPickList result = callLlm(description, candidates);

        if (result != null) {
            circuitBreaker.write("ai.set",
                    () -> redisTemplate.opsForValue().set(cacheKey, result, CacheNames.TTL_AI));
        }
        return result;
    }

    private String buildRerankKey(String description, List<Hotspot> candidates) {
        String ids = candidates.stream()
                .map(Hotspot::getHotspotId)
                .sorted()
                .map(String::valueOf)
                .reduce((x, y) -> x + "," + y)
                .orElse("");
        String raw = (description == null ? "" : description.trim().toLowerCase()) + "|" + ids;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return "ai:rerank:" + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "ai:rerank:" + raw.hashCode();
        }
    }

    private HotspotPickList callLlm(String description, List<Hotspot> candidates) {
        StringBuilder sb = new StringBuilder();
        for (Hotspot h : candidates) {
            sb.append("- id=").append(h.getHotspotId())
                    .append(" | name=").append(h.getHotspotName())
                    .append(" | address=").append(h.getAddress() == null ? "" : h.getAddress())
                    .append(" | desc=").append(trim(h.getDescription(), 160))
                    .append("\n");
        }

        String system = """
                Bạn là trợ lý gợi ý địa điểm du lịch. Người dùng mô tả mong muốn, bạn CHỌN và XẾP HẠNG
                các địa điểm phù hợp NHẤT chỉ trong danh sách được cung cấp.
                QUY TẮC BẮT BUỘC:
                - Chỉ được dùng hotspotId có trong danh sách. TUYỆT ĐỐI không bịa id mới.
                - score trong khoảng 0..1 (1 = phù hợp nhất).
                - reason ngắn gọn bằng tiếng Việt, giải thích vì sao phù hợp mô tả.
                """;

        String user = "Mô tả mong muốn của người dùng: \n" + description
                + "\n\nDanh sách địa điểm (candidate):\n" + sb;

        try {
            return chatClient.prompt()
                    .system(system)
                    .user(user)
                    .call()
                    .entity(HotspotPickList.class);
        } catch (Exception e) {
            log.warn("[AI] rerank lỗi: {}", e.getMessage());
            return null;
        }
    }
}
