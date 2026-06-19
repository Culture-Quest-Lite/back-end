package org.sep490.backend.module.user.specification;

import jakarta.persistence.criteria.Predicate;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.entity.enumeration.UserStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class UserSpecification {

    public static Specification<User> filterUsers(String search, UserStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(search)) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";

                Predicate searchPredicate = cb.or(
                        cb.like(cb.lower(root.get("username")), searchPattern),
                        cb.like(cb.lower(root.get("email")), searchPattern),
                        cb.like(cb.lower(root.get("displayName")), searchPattern)
                );
                predicates.add(searchPredicate);
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
