package org.sep490.backend.module.content.specification;

import jakarta.persistence.criteria.Predicate;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.content.entity.enumeration.TagStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class TagSpecification {

    private TagSpecification() {}

    public static Specification<Tag> filterTags(String search, TagStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(search)) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("tagName")), pattern));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("tagStatus"), status));
            } else {
                predicates.add(cb.notEqual(root.get("tagStatus"), TagStatus.DELETED));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

