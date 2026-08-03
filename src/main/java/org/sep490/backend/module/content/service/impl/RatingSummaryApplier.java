package org.sep490.backend.module.content.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.dto.projection.HotspotRatingSummaryProjection;
import org.sep490.backend.module.content.dto.projection.TargetRatingSummaryProjection;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.content.dto.response.RouteResponse;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.entity.enumeration.ReviewStatus;
import org.sep490.backend.module.content.repository.ReviewRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gán averageRating / totalReviews cho các response có thể được đánh giá.
 * Luôn dùng một truy vấn gộp cho cả danh sách để tránh N+1.
 * Khi chưa có đánh giá nào thì trả về 0.0 / 0 thay vì null.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RatingSummaryApplier {

    ReviewRepository reviewRepository;

    public HotspotResponse applyToHotspot(HotspotResponse response) {
        applyToHotspots(List.of(response));
        return response;
    }

    public void applyToHotspots(List<HotspotResponse> responses) {
        apply(responses,
                HotspotResponse::getHotspotId,
                ids -> reviewRepository.summarizeRatingsByHotspotIds(ids, ReviewStatus.ACTIVE).stream()
                        .collect(Collectors.toMap(HotspotRatingSummaryProjection::getHotspotId,
                                p -> new Summary(p.getAverageRating(), p.getTotalReviews()))),
                (response, summary) -> {
                    response.setAverageRating(summary.average());
                    response.setTotalReviews(summary.total());
                });
    }

    public RouteResponse applyToRoute(RouteResponse response) {
        applyToRoutes(List.of(response));
        return response;
    }

    public void applyToRoutes(List<RouteResponse> responses) {
        apply(responses,
                RouteResponse::getRouteId,
                ids -> toSummaryMap(reviewRepository.summarizeRatingsByRouteIds(ids, ReviewStatus.ACTIVE)),
                (response, summary) -> {
                    response.setAverageRating(summary.average());
                    response.setTotalReviews(summary.total());
                });
    }

    public StoryResponse applyToStory(StoryResponse response) {
        applyToStories(List.of(response));
        return response;
    }

    public void applyToStories(List<StoryResponse> responses) {
        apply(responses,
                StoryResponse::getStoryId,
                ids -> toSummaryMap(reviewRepository.summarizeRatingsByStoryIds(ids, ReviewStatus.ACTIVE)),
                (response, summary) -> {
                    response.setAverageRating(summary.average());
                    response.setTotalReviews(summary.total());
                });
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

    private record Summary(Double rawAverage, Long rawTotal) {

        static Summary empty() {
            return new Summary(null, null);
        }

        Double average() {
            return rawAverage == null ? 0.0 : Math.round(rawAverage * 10) / 10.0;
        }

        Long total() {
            return rawTotal == null ? 0L : rawTotal;
        }
    }
}
