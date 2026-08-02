package org.sep490.backend.module.content.specification;

import jakarta.persistence.criteria.Predicate;
import org.sep490.backend.module.content.dto.filter.ReviewFilterRequest;
import org.sep490.backend.module.content.entity.Review;
import org.sep490.backend.module.content.entity.enumeration.ReviewStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ReviewSpecification {

    private ReviewSpecification() {}

    public static Specification<Review> filter(ReviewFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            query.distinct(true);

            if (filter.getKeyword() != null && !filter.getKeyword().trim().isEmpty()) {
                String pattern = "%" + filter.getKeyword().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("comment")), pattern));
            }

            if (filter.getTargetType() != null && filter.getTargetId() != null) {
                switch (filter.getTargetType()) {
                    case HOTSPOT -> predicates.add(cb.equal(root.get("hotspot").get("hotspotId"), filter.getTargetId()));
                    case ROUTE -> predicates.add(cb.equal(root.get("route").get("routeId"), filter.getTargetId()));
                    case STORY -> predicates.add(cb.equal(root.get("story").get("storyId"), filter.getTargetId()));
                }
            } else if (filter.getTargetType() != null) {
                switch (filter.getTargetType()) {
                    case HOTSPOT -> predicates.add(cb.isNotNull(root.get("hotspot")));
                    case ROUTE -> predicates.add(cb.isNotNull(root.get("route")));
                    case STORY -> predicates.add(cb.isNotNull(root.get("story")));
                }
            }

            if (filter.getUserId() != null) {
                predicates.add(cb.equal(root.get("user").get("userId"), filter.getUserId()));
            }

            if (filter.getRating() != null) {
                predicates.add(cb.equal(root.get("rating"), filter.getRating()));
            }

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            } else {
                predicates.add(cb.equal(root.get("status"), ReviewStatus.ACTIVE));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
