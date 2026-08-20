package org.sep490.backend.module.content.specification;

import jakarta.persistence.criteria.Predicate;
import org.sep490.backend.module.content.dto.filter.StoryFilterRequest;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class StorySpecification {

    private static final Set<ContentStatus> MODERATION_ONLY =
            EnumSet.of(ContentStatus.PENDING_REVIEW, ContentStatus.REJECTED);

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

            // /api/v1/stories/** là endpoint công khai, không được để lộ nội dung
            // đang chờ duyệt hoặc đã bị từ chối dù client tự truyền status.
            if (filter.getStatus() != null && !MODERATION_ONLY.contains(filter.getStatus())) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            } else {
                predicates.add(cb.not(root.get("status").in(
                        ContentStatus.DELETED, ContentStatus.PENDING_REVIEW, ContentStatus.REJECTED)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
