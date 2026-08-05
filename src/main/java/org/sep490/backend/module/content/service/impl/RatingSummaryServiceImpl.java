package org.sep490.backend.module.content.service.impl;

import org.sep490.backend.module.content.service.inter.RatingSummaryService;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.config.redis.CacheNames;
import org.sep490.backend.config.redis.RedisCircuitBreaker;
import org.sep490.backend.module.content.dto.projection.HotspotRatingSummaryProjection;
import org.sep490.backend.module.content.dto.projection.TargetRatingSummaryProjection;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.content.dto.response.RouteResponse;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.entity.enumeration.ReviewStatus;
import org.sep490.backend.module.content.entity.enumeration.ReviewTargetType;
import org.sep490.backend.module.content.repository.ReviewRepository;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RatingSummaryServiceImpl implements RatingSummaryService {

    static Duration TTL = Duration.ofMinutes(10);

    ReviewRepository reviewRepository;
    RedisTemplate<String, Object> redisTemplate;
    RedisCircuitBreaker circuitBreaker;

    @Override
    public HotspotResponse applyToHotspot(HotspotResponse response) {
        applyToHotspots(List.of(response));
        return response;
    }

    @Override
    public void applyToHotspots(List<HotspotResponse> responses) {
        apply(responses,
                HotspotResponse::getHotspotId,
                ids -> loadWithCache(ReviewTargetType.HOTSPOT, ids,
                        missIds -> reviewRepository.summarizeRatingsByHotspotIds(missIds, ReviewStatus.ACTIVE).stream()
                                .collect(Collectors.toMap(HotspotRatingSummaryProjection::getHotspotId,
                                        p -> new Summary(p.getAverageRating(), p.getTotalReviews())))),
                (response, summary) -> {
                    response.setAverageRating(summary.average());
                    response.setTotalReviews(summary.total());
                });
    }

    @Override
    public RouteResponse applyToRoute(RouteResponse response) {
        applyToRoutes(List.of(response));
        return response;
    }

    @Override
    public void applyToRoutes(List<RouteResponse> responses) {
        apply(responses,
                RouteResponse::getRouteId,
                ids -> loadWithCache(ReviewTargetType.ROUTE, ids,
                        missIds -> toSummaryMap(
                                reviewRepository.summarizeRatingsByRouteIds(missIds, ReviewStatus.ACTIVE))),
                (response, summary) -> {
                    response.setAverageRating(summary.average());
                    response.setTotalReviews(summary.total());
                });
    }

    @Override
    public StoryResponse applyToStory(StoryResponse response) {
        applyToStories(List.of(response));
        return response;
    }

    @Override
    public void applyToStories(List<StoryResponse> responses) {
        apply(responses,
                StoryResponse::getStoryId,
                ids -> loadWithCache(ReviewTargetType.STORY, ids,
                        missIds -> toSummaryMap(
                                reviewRepository.summarizeRatingsByStoryIds(missIds, ReviewStatus.ACTIVE))),
                (response, summary) -> {
                    response.setAverageRating(summary.average());
                    response.setTotalReviews(summary.total());
                });
    }

    private Map<Long, Summary> loadWithCache(ReviewTargetType targetType,
                                             List<Long> ids,
                                             Function<List<Long>, Map<Long, Summary>> dbLoader) {
        Map<Long, Summary> result = new HashMap<>();
        List<Long> missIds = new ArrayList<>(ids);

        List<String> keys = ids.stream().map(id -> buildKey(targetType, id)).toList();
        List<Object> cached = circuitBreaker.read("rating.multiGet",
                () -> redisTemplate.opsForValue().multiGet(keys), null);

        if (cached != null && cached.size() == ids.size()) {
            missIds = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                if (cached.get(i) instanceof Summary hit) {
                    result.put(ids.get(i), hit);
                } else {
                    missIds.add(ids.get(i));
                }
            }
        }

        if (!missIds.isEmpty()) {
            Map<Long, Summary> fromDb = dbLoader.apply(missIds);
            Map<String, Object> toCache = new HashMap<>();
            for (Long id : missIds) {
                Summary summary = fromDb.getOrDefault(id, Summary.empty());
                result.put(id, summary);
                toCache.put(buildKey(targetType, id), summary);
            }
            writeBack(targetType, toCache);
        }
        return result;
    }

    private void writeBack(ReviewTargetType targetType, Map<String, Object> entries) {
        if (entries.isEmpty()) {
            return;
        }
        circuitBreaker.write("rating.multiSet", () -> {
            redisTemplate.opsForValue().multiSet(entries);
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                entries.keySet().forEach(key ->
                        connection.keyCommands().expire(
                                key.getBytes(StandardCharsets.UTF_8), TTL.getSeconds()));
                return null;
            });
        });
    }

    @Override
    public void evict(ReviewTargetType targetType, Long targetId) {
        if (targetType == null || targetId == null) {
            return;
        }
        circuitBreaker.write("rating.evict",
                () -> redisTemplate.delete(buildKey(targetType, targetId)));
    }

    private String buildKey(ReviewTargetType targetType, Long id) {
        return CacheNames.KEY_RATING + targetType.name().toLowerCase() + ":" + id;
    }

    private <T> void apply(List<T> responses,
                           Function<T, Long> idExtractor,
                           Function<List<Long>, Map<Long, Summary>> summaryLoader,
                           BiConsumer<T, Summary> setter) {
        if (responses == null || responses.isEmpty()) {
            return;
        }

        List<Long> ids = responses.stream()
                .map(idExtractor)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Summary> summaries = ids.isEmpty() ? Map.of() : summaryLoader.apply(ids);

        responses.forEach(response -> {
            Long id = idExtractor.apply(response);
            Summary summary = id == null ? null : summaries.get(id);
            setter.accept(response, summary == null ? Summary.empty() : summary);
        });
    }

    private Map<Long, Summary> toSummaryMap(List<TargetRatingSummaryProjection> projections) {
        return projections.stream()
                .collect(Collectors.toMap(TargetRatingSummaryProjection::getTargetId,
                        p -> new Summary(p.getAverageRating(), p.getTotalReviews())));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Summary {

        Double rawAverage;
        Long rawTotal;

        static Summary empty() {
            return new Summary(null, null);
        }

        @com.fasterxml.jackson.annotation.JsonIgnore
        public Double average() {
            return rawAverage == null ? 0.0 : Math.round(rawAverage * 10) / 10.0;
        }

        @com.fasterxml.jackson.annotation.JsonIgnore
        public Long total() {
            return rawTotal == null ? 0L : rawTotal;
        }
    }
}
