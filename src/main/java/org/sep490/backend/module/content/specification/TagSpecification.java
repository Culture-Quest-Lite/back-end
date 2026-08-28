package org.sep490.backend.module.content.specification;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.sep490.backend.common.utils.TextUtils;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.content.entity.enumeration.TagStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class TagSpecification {

    private static final Set<TagStatus> MODERATION_ONLY =
            EnumSet.of(TagStatus.PENDING_REVIEW, TagStatus.REJECTED);

    private TagSpecification() {}

    public static Specification<Tag> filterTags(String search, TagStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(search)) {
                // Bỏ dấu cả hai vế để "am thuc" khớp được "Ẩm thực" và ngược lại
                String pattern = "%" + TextUtils.removeDiacritics(search.trim()).toLowerCase() + "%";
                Expression<String> normalizedName = cb.function(
                        "unaccent", String.class, cb.lower(root.get("tagName")));
                predicates.add(cb.like(normalizedName, pattern));
            }

            // Đây là endpoint công khai (/api/tags/** nằm trong PUBLIC_GET_ENDPOINTS),
            // nên không bao giờ được trả về tag đang chờ duyệt hoặc đã bị từ chối,
            // kể cả khi client tự truyền status. Curator xem hai nhóm đó qua
            // /api/curator/content/pending và /api/curator/content/rejected.
            if (status != null && !MODERATION_ONLY.contains(status)) {
                predicates.add(cb.equal(root.get("tagStatus"), status));
            } else {
                predicates.add(root.get("tagStatus").in(TagStatus.ACTIVE, TagStatus.INACTIVE));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
