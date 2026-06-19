package org.sep490.backend.module.admin.specification;

import jakarta.persistence.criteria.Predicate;
import lombok.NoArgsConstructor;
import org.sep490.backend.module.admin.entity.SubscriptionPlan;
import org.sep490.backend.module.admin.entity.enumeration.SubscriptionPlanStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
public class SubscriptionPlanSpecification {

    public static Specification<SubscriptionPlan> filter(String search, SubscriptionPlanStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(search)) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("subscriptionPlanName")), pattern),
                                cb.like(cb.lower(root.get("subscriptionPlanDescription")), pattern)
                        )
                );
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            } else {
                predicates.add(cb.notEqual(root.get("status"), SubscriptionPlanStatus.DELETED));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
