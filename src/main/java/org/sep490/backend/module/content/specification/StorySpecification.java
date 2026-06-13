package org.sep490.backend.module.content.specification;

import jakarta.persistence.criteria.Predicate;
import org.sep490.backend.module.content.dto.filter.StoryFilterRequest;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.enums.ContentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class StorySpecification {

    private StorySpecification() {}

    public static Specification<Story> filter(StoryFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            query.distinct(true);

            if (filter.getKeyword() != null && !filter.getKeyword().trim().isEmpty()) {
                String pattern = "%" + filter.getKeyword().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("content")), pattern)
                ));
            }

            if (filter.getTagId() != null) {
                predicates.add(cb.equal(root.get("tag").get("tagId"), filter.getTagId()));
            }

            if (filter.getHotspotId() != null) {
                predicates.add(cb.equal(root.get("hotspot").get("hotspotId"), filter.getHotspotId()));
            }

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            } else {
                predicates.add(cb.notEqual(root.get("status"), ContentStatus.DELETED));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
