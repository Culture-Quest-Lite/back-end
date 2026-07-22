package org.sep490.backend.module.admin.specification;

import jakarta.persistence.criteria.Predicate;
import lombok.NoArgsConstructor;
import org.sep490.backend.module.admin.entity.enumeration.AuditAction;
import org.sep490.backend.module.authentication.entity.AuditLog;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
public class AuditLogSpecification {

    public static Specification<AuditLog> filter(String search, Long userId, AuditAction action,
                                                 String tableName, LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(search)) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("recordId")), pattern),
                                cb.like(cb.lower(root.get("endpoint")), pattern)
                        )
                );
            }

            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("userId"), userId));
            }

            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }

            if (StringUtils.hasText(tableName)) {
                predicates.add(cb.equal(cb.lower(root.get("tableName")), tableName.trim().toLowerCase()));
            }

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }

            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
