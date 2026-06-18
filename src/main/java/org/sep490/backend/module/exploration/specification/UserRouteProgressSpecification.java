package org.sep490.backend.module.exploration.specification;

import jakarta.persistence.criteria.Predicate;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.exploration.dto.filter.UserRouteProgressFilter;
import org.sep490.backend.module.exploration.entity.UserRouteProgress;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class UserRouteProgressSpecification {

    public static Specification<UserRouteProgress> filterProgress(UserRouteProgressFilter filter, User user) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (user != null) {
                predicates.add(cb.equal(root.get("user"), user));
            }

            if (filter != null) {
                if (filter.getStatus() != null) {
                    predicates.add(cb.equal(root.get("status"), filter.getStatus()));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
